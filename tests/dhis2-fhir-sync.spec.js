const {
    expect,
    test
} = require("@playwright/test");

test.beforeEach(async function({
    request
}) {
    expect((await request.delete(
        "http://localhost:8081/fhir/Consent", {
            "params": {
                "patient.identifier": "urn:dhis2:anc:uniqueId|8879798"
            }
        })).status()).toBe(200);

    expect((await request.delete(
        "http://localhost:8081/fhir/Patient", {
            "params": {
                "identifier": "urn:dhis2:anc:uniqueId|8879798"
            }
        })).status()).toBe(200);
});

test("should successfully sync tracked entity with EHR",
    async function({
        request
    }) {
        expect((await (await request.get(
            "http://localhost:8081/fhir/Patient?_format=json", {
                headers: {
                    "Cache-Control": "no-store"
                }
            }
        )).json()).total).toBe(0)

        const trackedEntity = {
            "trackedEntities": [{
                "enrollments": [{
                    "attributes": [{
                        "attribute": "PpEGiQurAll",
                        "value": "8879798"
                    }, {
                        "attribute": "sB1IHYu2xQT",
                        "value": "John"
                    }, {
                        "attribute": "ENRjVGxVL6l",
                        "value": "Doe"
                    }, {
                        "attribute": "NI0QRzJvQ0k",
                        "value": "1990-04-05"
                    }, {
                        "attribute": "QjdN4mhh4UN",
                        "value": "true"
                    }, {
                        "attribute": "rYnea37ReDs",
                        "value": "true"
                    }, {
                        "attribute": "MRGgEyilusR",
                        "value": "true"
                    }, {
                        "attribute": "ruUzdQRiYpy",
                        "value": "true"
                    }, {
                        "attribute": "AoOp84H5Vt1",
                        "value": "true"
                    }, {
                        "attribute": "NihUionWia1",
                        "value": "true"
                    }, {
                        "attribute": "qJdyXIggXXJ",
                        "value": "true"
                    }, {
                        "attribute": "B6TnnFMgmCk",
                        "value": "35"
                    }],
                    "enrolledAt": "2025-09-04",
                    "events": [{
                        "orgUnit": "oQlmngiVAes",
                        "program": "WSGAb5XwJ3Y",
                        "programStage": "XznDErihed9",
                        "scheduledAt": "2025-09-04",
                        "status": "SCHEDULE"
                    }, {
                        "orgUnit": "oQlmngiVAes",
                        "program": "WSGAb5XwJ3Y",
                        "programStage": "eR4sNwxkd9Q",
                        "scheduledAt": "2025-09-04",
                        "status": "SCHEDULE"
                    }],
                    "occurredAt": "2025-09-04",
                    "orgUnit": "oQlmngiVAes",
                    "program": "WSGAb5XwJ3Y",
                    "status": "ACTIVE"
                }],
                "orgUnit": "oQlmngiVAes",
                "trackedEntityType": "MCPQUTHX1Ze"
            }]
        }

        const trackedEntityResponse = await request.post(
            "api/tracker?async=false", {
                data: trackedEntity,
                headers: {
                    "Authorization": "Basic YWRtaW46ZGlzdHJpY3Q=",
                    "Content-Type": "application/json"
                }
            });
        expect(trackedEntityResponse.status()).toBe(200);

        await expect.poll(async function() {
            return (await (await request.get(
                "http://localhost:8081/fhir/Patient?_format=json", {
                    headers: {
                        "Cache-Control": "no-store"
                    }
                }
            )).json()).total
        }).toBe(1);
    });