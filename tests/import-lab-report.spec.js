const {
    expect,
    test
} = require("@playwright/test");
const {
    execSync
} = require("node:child_process");

test("should successfully import the lab report into DHIS2",
    async function({
                       request
                   }) {
        const specimenId = Math.floor(Math.random() * 10000).toString();
        const newEnrollment = await request.post(
            "/api/tracker", {
                params: {
                    "async": false
                },
                data: {
                    trackedEntities: [{
                        orgUnit: "DiszpKrYNg8",
                        trackedEntityType: "MCPQUTHX1Ze",
                        attributes: [{
                            attribute: "tvaF9No9nkF",
                            value: "EBOLA"
                        }],
                        enrollments: [{
                            orgUnit: "DiszpKrYNg8",
                            program: "N07iEegH3Hw",
                            enrolledAt: "2026-09-18",
                            occurredAt: "2026-09-18",
                            status: "ACTIVE",
                            events: [{
                                programStage: "wVrLHHbixoP",
                                orgUnit: "DiszpKrYNg8",
                                scheduledAt: "2026-09-18",
                                program: "N07iEegH3Hw",
                                status: "SCHEDULE"
                            },
                                {
                                    programStage: "d62zsvlENzr",
                                    orgUnit: "DiszpKrYNg8",
                                    occurredAt: "2026-09-18",
                                    program: "N07iEegH3Hw",
                                    status: "COMPLETED",
                                    dataValues: [{
                                        dataElement: "TS6Yt0weEhi",
                                        value: specimenId
                                    }]
                                }
                            ]
                        }]
                    }]
                }
            });

        expect(newEnrollment.status()).toBe(200);
        const enrollmentId = (await newEnrollment.json()).bundleReport.typeReportMap.ENROLLMENT.objectReports[0].uid;

        execSync('bru run fetch-lab-requests-from-dhis2.yml', {
            cwd: "tests/create-fake-lab-diagnostic-report-collection",
            stdio: 'inherit',
        });

        await expect.poll(async function() {
            return (await (await request.get(
                "/api/tracker/enrollments/" + enrollmentId, {
                    params: {
                        "fields": "*"
                    }
                }
            )).json()).events
                .filter(
                    (e) =>
                        e.programStage ===
                        "mDvE6kdNpty" &&
                        e.status === "COMPLETED",
                )
                .flatMap((e) =>
                    e.dataValues
                )
                .filter(
                    (dv) =>
                        dv.dataElement === "TS6Yt0weEhi" &&
                        dv.value === specimenId
                ).length
        }, {
            timeout: 40000
        }).toBe(1);
    });