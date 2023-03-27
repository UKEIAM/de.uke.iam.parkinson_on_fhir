import requests
import unittest
import re
import string
import random

# The server where the REST interface run. By default, this points to the Docker host.
SERVER = "http://172.17.0.1:50202/parkinson-fhir"


class TestDevice(unittest.TestCase):
    def __init__(self, *kargs, **kwargs) -> None:
        super().__init__(*kargs, **kwargs)
        self.resource_url = ""
        self.payload = {
            "resourceType": "Device",
            "distinctIdentifier": "ExampleDevice",
        }

    def setUp(self):
        r = requests.post(f"{SERVER}/Device", json=self.payload)
        self.assertEqual(r.status_code, 201, msg=r.text)
        self.resource_url = r.headers["location"]

    def testInsertAndDelete(self):
        pass

    def testFailOnMultipleRun(self):
        r = requests.post(f"{SERVER}/Device", json=self.payload)
        self.assertEqual(r.status_code, 422, msg=r.text)

    def testGet(self):
        r = requests.get(self.resource_url)
        self.assertEqual(r.status_code, 200, msg=r.text)

    def testDeleteNonexisting(self):
        r = requests.delete(f"{SERVER}/Device/AnotherExampleDevice")
        self.assertEqual(r.status_code, 404, msg=r.text)

    def tearDown(self):
        r = requests.delete(self.resource_url)
        self.assertEqual(r.status_code, 204, msg=r.text)


class TestPatient(unittest.TestCase):
    def __init__(self, *kargs, **kwargs) -> None:
        super().__init__(*kargs, **kwargs)
        self.resource_url = ""
        self.payload = {
            "resourceType": "Patient",
            "active": True,
            "identifier": {"value": "John Doe"},
        }

    def setUp(self):
        r = requests.post(f"{SERVER}/Patient", json=self.payload)
        self.assertEqual(r.status_code, 201, msg=r.text)
        self.resource_url = r.headers["location"]

    def testInsertAndDelete(self):
        pass

    def testGet(self):
        r = requests.get(self.resource_url)
        self.assertEqual(r.status_code, 200, msg=r.text)

    def testGetByIdentifier(self):
        # Get patient by identifier value
        r = requests.get(f"{SERVER}/Patient", params={"identifier": "John Doe"})
        self.assertEqual(r.status_code, 200, msg=r.text)

    def testDeleteNonexisting(self):
        r = requests.delete(f"{SERVER}/Patient/42")
        self.assertEqual(r.status_code, 404, msg=r.text)

    def tearDown(self):
        r = requests.delete(self.resource_url)
        self.assertEqual(r.status_code, 204, msg=r.text)


class TestGroup(unittest.TestCase):
    def __init__(self, *kargs, **kwargs) -> None:
        super().__init__(*kargs, **kwargs)
        self.resource_url = ""
        self.payload = {
            "resourceType": "Group",
            "active": True,
            "actual": True,
            "type": "person",
            "name": "Test Group",
        }

    def setUp(self):
        r = requests.post(f"{SERVER}/Group", json=self.payload)
        self.assertEqual(r.status_code, 201, msg=r.text)
        self.resource_url = r.headers["location"]

    def testInsertAndDelete(self):
        pass

    def testGet(self):
        r = requests.get(self.resource_url)
        self.assertEqual(r.status_code, 200, msg=r.text)

    def testDeleteNonexisting(self):
        r = requests.delete(f"{SERVER}/Group/42")
        self.assertEqual(r.status_code, 404, msg=r.text)

    def tearDown(self):
        r = requests.delete(self.resource_url)
        self.assertEqual(r.status_code, 204, msg=r.text)


class TestObservation(unittest.TestCase):
    def __init__(self, *kargs, **kwargs) -> None:
        super().__init__(*kargs, **kwargs)
        self.subject_url = ""
        self.subject_reference = ""
        self.device_url = ""
        self.observation_urls = []
        self.payload = {
            "resourceType": "Observation",
            "status": "final",
            "category": [
                {
                    "coding": [
                        {
                            "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                            "code": "procedure",
                            "display": "Procedure",
                        }
                    ]
                }
            ],
            "code": {
                "coding": [
                    {
                        "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                        "code": "procedure",
                        "display": "Procedure",
                    }
                ]
            },
            "effectiveInstant": "2015-02-07T13:28:17.239+02:00",
            "subject": {"reference": "Patient/THIS_WILL_BE_REPLACED"},
            "bodySite": {
                "coding": [
                    {
                        "system": "Custom",
                        "code": "leftWrist",
                        "display": "Left wrist",
                    }
                ]
            },
            "device": {"reference": "Device/THIS_WILL_BE_REPLACED"},
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
                    "valueQuantity": {"value": 1.0, "unit": "m/s^2"},
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
                    "valueQuantity": {"value": 2.0, "unit": "m/s^2"},
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
                    "valueQuantity": {"value": 3.0, "unit": "m/s^2"},
                },
            ],
        }

    def setUp(self):
        # Create the subject
        r = requests.post(
            f"{SERVER}/Patient",
            json={
                "resourceType": "Patient",
                "active": True,
                "identifier": {"value": "Example Patient"},
            },
        )
        self.assertEqual(r.status_code, 201, msg=r.text)
        self.subject_url = r.headers["location"]
        self.subject_reference = TestObservation._extractRelativeReference(
            r.headers["location"]
        )
        self.payload["subject"] = {"reference": self.subject_reference}

        # Create the device with a unique name
        device_name = "".join(
            random.choices(string.ascii_uppercase + string.digits, k=6)
        )
        r = requests.post(
            f"{SERVER}/Device",
            json={
                "resourceType": "Device",
                "distinctIdentifier": device_name,
            },
        )
        self.assertEqual(r.status_code, 201, msg=r.text)
        self.device_url = r.headers["location"]

        # Prepare and add the first payload
        self.payload["device"] = {
            "reference": TestObservation._extractRelativeReference(
                r.headers["location"]
            )
        }
        r = requests.post(f"{SERVER}/Observation", json=self.payload)
        self.assertEqual(r.status_code, 201, msg=r.text)
        self.observation_urls.append(r.headers["location"])

        # Add a second payload
        self.payload["effectiveInstant"] = "2010-02-07T13:28:17.239+02:00"
        r = requests.post(f"{SERVER}/Observation", json=self.payload)
        self.assertEqual(r.status_code, 201, msg=r.text)
        self.observation_urls.append(r.headers["location"])

    def tearDown(self) -> None:
        for url in self.observation_urls:
            r = requests.delete(url)
            self.assertEqual(r.status_code, 204, msg=r.text)

    def testInsertAndDelete(self):
        pass

    def testGet(self):
        r = requests.get(
            f"{SERVER}/Observation?category=procedure&subject={self.subject_reference}&date=ge2011-01-02"
        )

        response = r.json()
        self.assertEqual(r.status_code, 200, msg=r.text)
        self.assertEqual(len(response["entry"]), 1)
        for key, value in response["entry"][0]["resource"].items():
            # We must check for the correct timestamp here - looks fine!
            if key == "effectiveInstant":
                self.assertEqual(value, "2015-02-07T11:28:17.239+00:00")
                continue
            elif key == "id":
                continue

            self.assertEqual(
                self.payload[key],
                value,
                msg=f"Key '{key}'",
            )

    def testBundle(self):
        # Build Bundle
        entry1 = self.payload.copy()
        entry2 = self.payload.copy()
        entry1["effectiveInstant"] = "2020-02-07T13:28:17.239+02:00"
        entry2["effectiveInstant"] = "2020-03-10T13:28:18.240+02:00"
        bundle_payload = {
            "resourceType": "Bundle",
            "type": "batch",
            "entry": [{"resource": entry1}, {"resource": entry2}],
        }

        r = requests.post(f"{SERVER}", json=bundle_payload)
        self.assertEqual(r.status_code, 200, msg=r.text)

        # Ensure proper response - the status code might not indicate that!
        entries = r.json()["entry"]
        self.assertEqual(len(entries), 2, msg=entries)
        for entry in entries:
            response = entry["response"]
            self.assertEqual(response["status"], "201 Created", msg=entries)

            # Remove the resources during cleaning
            self.observation_urls.append(f"{SERVER}/{response['location']}")

    @staticmethod
    def _extractRelativeReference(value: str) -> str:
        relative_reference = re.search(r".*\/([A-Za-z]+\/.+)$", value)
        return relative_reference.group(1)


class TestFhirApi(unittest.TestCase):
    """
    Integration tests for the FHIR API. In general, the database must be clear with each start.
    However, to some extend randomness is introduced for ensuring successfull runs nevertheless.
    """

    def test_config(self):
        r = requests.get(f"{SERVER}/metadata")
        self.assertEqual(r.status_code, 200)


if __name__ == "__main__":
    unittest.main()
