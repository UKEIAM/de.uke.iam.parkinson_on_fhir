import requests
import unittest


class TestFhirApi(unittest.TestCase):

    # The server where the REST interface run. By default, this points to the Docker host.
    SERVER = "http://172.17.0.1:50202/parkinson-fhir"

    def test_config(self):
        r = requests.get(f"{TestFhirApi.SERVER}/metadata")
        self.assertEqual(r.status_code, 200)

    def test_device_insert(self):
        payload = {
            "resourceType": "Device",
            "distinctIdentifier": "This is an example device",
        }
        r = requests.post(f"{TestFhirApi.SERVER}/Device", json=payload)
        self.assertEqual(r.status_code, 201)

        # Inserting the same thing will fail
        r = requests.post(f"{TestFhirApi.SERVER}/Device", json=payload)
        self.assertEqual(r.status_code, 422)

    def test_patient(self):
        payload = {
            "resourceType": "Patient",
            "active": True,
            "text": {"status": "additional", "div": "Some metadata"},
        }

        r = requests.post(f"{TestFhirApi.SERVER}/Patient", json=payload)
        self.assertEqual(r.status_code, 201)

    def test_group(self):
        payload = {
            "resourceType": "Group",
            "active": True,
            "actual": True,
            "type": "person",
            "name": "Test Group",
        }

        r = requests.post(f"{TestFhirApi.SERVER}/Group", json=payload)
        self.assertEqual(r.status_code, 201)

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

        r = requests.post(f"{TestFhirApi.SERVER}/Observation", json=payload)
        print(r.text)
        self.assertEqual(r.status_code, 201)


if __name__ == "__main__":
    unittest.main()
