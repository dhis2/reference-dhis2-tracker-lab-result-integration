/*
 * Copyright (c) 2004-2025, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.integration.camel.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.parser.IParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hisp.dhis.api.model.v42_4.EnrollmentRef;
import org.hisp.dhis.api.model.v42_4.EventsRefRef;
import org.hisp.dhis.api.model.v42_4.TrackerAttribute;
import org.hisp.dhis.api.model.v42_4.TrackerDataValue;
import org.hisp.dhis.api.model.v42_4.TrackerEnrollment;
import org.hisp.dhis.api.model.v42_4.TrackerEvent;
import org.hisp.dhis.api.model.v42_4.TrackerImportReport;
import org.hisp.dhis.api.model.v42_4.TrackerTrackedEntity;
import org.hisp.dhis.integration.camel.AbstractFunctionalTestCase;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@UseAdviceWith
public class ImportLabResultsRouteFunctionalTestCase extends AbstractFunctionalTestCase {
    @Autowired
    private CamelContext camelContext;

    @Autowired
    private Dhis2Client dhis2Client;

    @Value("${dhis2.program.id}")
    private String dhis2ProgramId;

    @Value("${dhis2.program.labRequestProgramStage.id}")
    private String dhis2LabRequestProgramStageId;

    @Value("${dhis2.program.labResultProgramStage.id}")
    private String dhis2LabResultProgramStageId;

    @Value("${dhis2.program.specimenDataElement.id}")
    private String dhis2SpecimenDataElementId;

    @Test
    public void test() throws Exception {
        String specimenIdUnderTest = "12345678";

        AdviceWith.adviceWith(
                camelContext,
                "fetchDiagnosticReportRoute",
                r -> r.weaveById("searchDiagnosticReport").replace().to("mock:fhir"));

        MockEndpoint mockFhirEndpoint = camelContext.getEndpoint("mock:fhir", MockEndpoint.class);
        IParser iParser = FhirVersionEnum.R4.newContext().newJsonParser();
        mockFhirEndpoint.returnReplyBody(
                new Expression() {
                    @Override
                    public <T> T evaluate(Exchange exchange, Class<T> type) {
                        try {
                            String diagnosticReportAsJson =
                                    FileUtils.readFileToString(
                                            new File(
                                                    getClass()
                                                            .getClassLoader()
                                                            .getResource("diagnosticReports.json")
                                                            .getFile()), Charset.defaultCharset());
                            Bundle bundle = new Bundle();
                            iParser.parseInto(diagnosticReportAsJson.replace("<SPECIMEN_ACCESSION>", specimenIdUnderTest), bundle);
                            return (T) bundle;
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });

        AdviceWith.adviceWith(
                camelContext,
                "importLabResultRoute",
                r -> r.weaveAddLast().to("mock:spy"));
        MockEndpoint spyEndpoint = camelContext.getEndpoint("mock:spy", MockEndpoint.class);
        spyEndpoint.setResultWaitTime(120000);
        spyEndpoint.setExpectedCount(1);

        dhis2Client
                .post("dataStore/iol/diagnosticReportTransfromScript")
                .withResource(
                        IOUtils.toString(
                                new File("../config/dhis2/diagnosticReportTransfromScript.json").toURI(),
                                Charset.defaultCharset()))
                .transfer()
                .close();

        String orgUnit = "DiszpKrYNg8";
        TrackerTrackedEntity trackedEntity =
                new TrackerTrackedEntity()
                        .withOrgUnit(orgUnit)
                        .withTrackedEntityType("MCPQUTHX1Ze")
                        .withAttributes(
                                List.of(
                                        new TrackerAttribute().withAttribute("gO00x3YrZMH").withValue("BIZ605755"),
                                        new TrackerAttribute().withAttribute("tvaF9No9nkF").withValue("EBOLA"),
                                        new TrackerAttribute().withAttribute("fncDrNotzeS"),
                                        new TrackerAttribute().withAttribute("bSssQxhP8Ic")))
                        .withEnrollments(enrol(orgUnit, specimenIdUnderTest));

        String trackedEntityId =
                dhis2Client
                        .post("tracker")
                        .withResource(Map.of("trackedEntities", List.of(trackedEntity)))
                        .withParameter("async", "false")
                        .transfer()
                        .returnAs(TrackerImportReport.class)
                        .getBundleReport()
                        .get()
                        .getTypeReportMap()
                        .get()
                        .getAdditionalProperties()
                        .get("TRACKED_ENTITY")
                        .getObjectReports()
                        .get()
                        .get(0)
                        .getUid()
                        .get()
                        .toString();

        camelContext.start();

        spyEndpoint.assertIsSatisfied();

        List<EventsRefRef> labResultEvents = Lists.newArrayList(dhis2Client
                .get("tracker/events")
                .withField("*").withoutPaging()
                .withParameter("program", dhis2ProgramId)
                .withParameter("status", EventsRefRef.EventStatus.COMPLETED.value())
                .withParameter("programStage", dhis2LabResultProgramStageId)
                .withParameter("trackedEntity", trackedEntityId)
                .transfer().returnAs(EventsRefRef.class, "events"));

        assertEquals(1, labResultEvents.size());
        assertEquals(dhis2SpecimenDataElementId, labResultEvents.get(0).getDataValues().get().get(0).getDataElement().get());
        assertEquals(specimenIdUnderTest, labResultEvents.get(0).getDataValues().get().get(0).getValue().get());
    }

    public List<TrackerEnrollment> enrol(String orgUnitId, String specimenId) {
        List<TrackerEvent> events = new ArrayList<>();

        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        events.add(
                new TrackerEvent()
                        .withProgramStage("wVrLHHbixoP")
                        .withOrgUnit(orgUnitId)
                        .withScheduledAt(today)
                        .withProgram(dhis2ProgramId)
                        .withStatus(EventsRefRef.EventStatus.SCHEDULE));

        events.add(
                new TrackerEvent()
                        .withProgramStage(dhis2LabRequestProgramStageId)
                        .withOrgUnit(orgUnitId)
                        .withOccurredAt(today)
                        .withProgram(dhis2ProgramId)
                        .withStatus(EventsRefRef.EventStatus.COMPLETED)
                        .withDataValues(
                                List.of(new TrackerDataValue().withDataElement(dhis2SpecimenDataElementId).withValue(specimenId))));

        return List.of(
                new TrackerEnrollment()
                        .withOrgUnit(orgUnitId)
                        .withProgram(dhis2ProgramId)
                        .withEnrolledAt(today)
                        .withAttributes(
                                List.of(
                                        new TrackerAttribute().withAttribute("gO00x3YrZMH").withValue("BIZ605755"),
                                        new TrackerAttribute().withAttribute("fncDrNotzeS"),
                                        new TrackerAttribute().withAttribute("ENRjVGxVL6l")))
                        .withOccurredAt(today)
                        .withStatus(EnrollmentRef.EnrollmentStatus.ACTIVE)
                        .withEvents(events));
    }
}
