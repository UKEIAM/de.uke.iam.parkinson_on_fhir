from dataclasses import dataclass, field
import regex as re
import multiprocessing
from typing import Sequence
import random
import time

import requests

def __create_request(input) -> float:
    server, payload = input
    start = time.perf_counter()
    r = requests.post(f"{server}/Observation", json=payload)
    duration = time.perf_counter() - start
    return duration if r.status_code != 201 else float('nan')

@dataclass
class Benchmark:
    server: str = "http://172.17.0.1:50202/parkinson-fhir"
    subject_reference: str = field(init=False)
    device_reference: str = field(init=False)

    def __post_init__(self):
        try:
            r = requests.post(
                f"{self.server}/Patient",
                json={
                    "resourceType": "Patient",
                    "active": True,
                    "identifier": {"value": "Example Patient"},
                },
            )
            assert r.status_code == 201
            self.subject_reference = Benchmark._extractRelativeReference(r.headers["location"])
        except:
            raise ValueError("Unable to create example subject")

        try:
            r = requests.post(
                f"{self.server}/Device",
                json={
                    "resourceType": "Device",
                    "distinctIdentifier": "Example device",
                },
            )
            assert r.status_code == 201
            self.device_reference = Benchmark._extractRelativeReference(r.headers["location"])
        except:
            raise ValueError("Unable to create example subject")
        
    def benchmark(self, num_requests: int, num_worker: int) -> Sequence[float]:
        rng = random.Random()
        request_values = [
            (self.server, {
                "resourceType": "Observation",
                "status": "final",
                "category": {
                    "coding": [
                        {
                            "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                            "code": "procedure",
                            "display": "Procedure",
                        }
                    ]
                },
                "code": {
                    "coding": [
                        {
                            "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                            "code": "procedure",
                            "display": "Procedure",
                        }
                    ]
                },
                # FIXME: Need to be different for each call.
                "effectiveInstant": "2015-02-07T13:28:17.239+02:00",
                "subject": {"reference": self.subject_reference},
                "bodySite": {
                    "coding": [
                        {
                            "system": "Custom",
                            "code": "leftWrist",
                            "display": "Left wrist",
                        }
                    ]
                },
                "device": {"reference": self.device_reference},
                "component": [
                    {
                        "code": {
                            "coding": [
                                {
                                    "system": "http://loinc.org",
                                    "code": "X42",
                                    "display": "Acceleration on the X axis",
                                }
                            ],
                        },
                        "valueQuantity": {"value": rng.uniform(-3.0, 3.0), "unit": "m/s^2"},
                    },
                    {
                        "code": {
                            "coding": [
                                {
                                    "system": "http://loinc.org",
                                    "code": "X43",
                                    "display": "Acceleration on the Y axis",
                                }
                            ],
                        },
                        "valueQuantity": {"value": rng.uniform(-3.0, 3.0), "unit": "m/s^2"},
                    },
                    {
                        "code": {
                            "coding": [
                                {
                                    "system": "http://loinc.org",
                                    "code": "X44",
                                    "display": "Acceleration on the Z axis",
                                }
                            ],
                        },
                        "valueQuantity": {"value": rng.uniform(-3.0, 3.0), "unit": "m/s^2"},
                    },
                ],
            })
            for _ in range(num_requests)
        ]

        with multiprocessing.Pool(num_worker) as p:
            return p.map(__create_request, request_values)


    @staticmethod
    def _extractRelativeReference(value: str) -> str:
        relative_reference = re.search(r".*\/([A-Za-z]+\/.+)$", value)
        return relative_reference.group(1)

if __name__ == "__main__":
    # TODO
    pass