from dataclasses import dataclass, field
import re
import multiprocessing
from typing import Sequence, Tuple
import random
import time
import csv
import datetime

import requests

def __create_request(input) -> float:
    server, payload = input
    start = time.perf_counter()
    r = requests.post(f"{server}/Observation", json=payload)
    duration = time.perf_counter() - start
    return duration if r.status_code in range(200,300) else float('nan')

@dataclass
class Benchmark:
    server: str = "http://172.17.0.1:50506/parkinson-fhir"
    num_worker: int = 2
    subject_reference: str = field(init=False)
    device_reference: str = field(init=False)
    reference_date: str = datetime.datetime.fromisoformat("2015-02-07T13:28:17.239+02:00")

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
            #print(f"{r}, text: {r.text}, content: {r.content}, code: {r.status_code}")
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
            #print(f"{r}, text: {r.text}, content: {r.content}, code: {r.status_code}")
            assert r.status_code == 201
            self.device_reference = Benchmark._extractRelativeReference(r.headers["location"])
        except:
            raise ValueError("Unable to create example subject")

    def create_new_data_by_adding_seconds(self, seconds: int) -> str:
        new_date = self.reference_date + datetime.timedelta(0,seconds)
        return new_date.strftime('%Y-%m-%dT%H:%M:%S.%f')[:-3]


    def create_request_data(self, num_requests: int) -> Sequence[Tuple[str, dict]]:
        rng = random.Random()
        request_values = [
            (self.server, {
                "resourceType": "Observation",
                "status": "final",
                "category": [{
                    "coding": [
                        {
                            "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                            "code": "procedure",
                            "display": "Procedure",
                        }
                    ]
                }],
                "code": {
                    "coding": [
                        {
                            "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                            "code": "procedure",
                            "display": "Procedure",
                        }
                    ]
                },
                "effectiveInstant": self.create_new_data_by_adding_seconds(i),
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
            for i in range(num_requests)
        ]

        return request_values
        


    @staticmethod
    def _extractRelativeReference(value: str) -> str:
        relative_reference = re.search(r".*\/([A-Za-z]+\/.+)$", value)
        return relative_reference.group(1)

if __name__ == "__main__":
    # benchmark specs
    num_worker = 4; num_requests = 100000
    hapi_server = "http://172.17.0.1:50505/fhir"
    hapi = True
    if hapi:
        benchmark = Benchmark(server=hapi_server, num_worker = num_worker)
        f_name = f"src/evaluation/benchmarks/benchmark_HAPI_{num_worker}_{num_requests}.csv"
    else:
        benchmark = Benchmark(num_worker = num_worker)
        f_name = f"src/evaluation/benchmarks/benchmark_{num_worker}_{num_requests}.csv"
    request_values = benchmark.create_request_data(num_requests=num_requests)
    # perform benchmark
    with multiprocessing.Pool(benchmark.num_worker) as p:
            timings = p.map(__create_request, request_values)
    # write out results
    
    with open(f_name, "w+", newline="") as f:
        writer = csv.writer(f)
        writer.writerows([[timing] for timing in timings])

