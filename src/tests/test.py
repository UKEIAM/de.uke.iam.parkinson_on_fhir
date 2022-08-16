import requests
import unittest
import random
import string

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


class TestFhirApi(unittest.TestCase):
    """
    Integration tests for the FHIR API. In general, the database must be clear with each start.
    However, to some extend randomness is introduced for ensuring successfull runs nevertheless.
    """

    def test_config(self):
        r = requests.get(f"{SERVER}/metadata")
        self.assertEqual(r.status_code, 200)

    def test_observation(self):
        payload = {
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
            "effectiveDateTime": "2015-02-07T13:28:17",
            "subject": "Patient/1",
            "bodySite": {
                "coding": [
                    {
                        "system": "Custom",
                        "code": "leftWrist",
                        "display": "leftWrist",
                    }
                ]
            },
            "device": {
                "type": "Device",
                "identifier": "1",
            },
            "component": [
                {
                    "coding": {
                        "coding": [
                            {
                                "system": "http://loinc.org",
                                "code": "X42",
                                "display": "Acceleration on the X axis",
                            }
                        ]
                    },
                    "valueQuantity": {"value": 1.0, "unit": "m/s^2"},
                },
                {
                    "coding": {
                        "coding": [
                            {
                                "system": "http://loinc.org",
                                "code": "X43",
                                "display": "Acceleration on the Y axis",
                            }
                        ]
                    },
                    "valueQuantity": {"value": 2.0, "unit": "m/s^2"},
                },
                {
                    "coding": {
                        "coding": [
                            {
                                "system": "http://loinc.org",
                                "code": "X44",
                                "display": "Acceleration on the Z axis",
                            }
                        ]
                    },
                    "valueQuantity": {"value": 3.0, "unit": "m/s^2"},
                },
            ],
        }

        r = requests.post(f"{SERVER}/Observation", json=payload)
        self.assertEqual(r.status_code, 201, msg=r.text)

    @staticmethod
    def generate_unique_name(lenght: int = 7) -> str:
        return "".join(random.choices(string.ascii_uppercase + string.digits, k=lenght))


if __name__ == "__main__":
    unittest.main()
