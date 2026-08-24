# DHIS2 Tracker Lab Result Integration - reference implementation [DRAFT]

1. [What is this implementation?](#what-is-this-implementation)
2. [Quick Start](#quick-start)
    + [Monitoring & Management](#monitoring--management)
3. [Overview](#overview)
    + [DHIS2](#dhis2)
    + [Lab Information System](#lab-information-system)
    + [Interoperability Layer](#interoperability-layer)
4. [Test Kit](#publisher-configuration)
5. [Consumer Configuration](#consumer-configuration)
6. [Features](#features)
    + [Synchronisation Approval](#synchronisation-approval)
    + [Full synchronisation](#full-synchronisation)
        - [Publisher](#publisher-1)
        - [Consumer](#consumer-1)
    + [Synchronisation replay](#synchronisation-replay)
    + [Metadata Resource Mapping](#metadata-resource-mapping)
7. [Adaptation](#adaptation)
    + [Non-DHIS2 target](#non-dhis2-target)
    + [Approval workflow](#approval-workflow)
    + [Synchronised resources](#synchronised-resources)
    + [Running E2E Tests](#running-e2e-tests)
8. [Performance Considerations](#performance-considerations)
    + [Hardware Requirements](#hardware-requirements)
9. [Support](#support)

## What is this implementation?

DHIS2 Tracker programmes can be configured to support case-based disease surveillance and EMR functions such as patient health record. Often lab results need to be entered into such programmes to inform the decision-making of health professionals. In contrast to manual data entry, sending electronically the lab results to DHIS2 helps speed up this decision-making, eliminate manual transcribing errors between systems, as well as improve the information availability to all health and management staff. A Laboratory Information System (LIS) is typically the primary source of lab results destined to other information systems. With the generous support of US CDC, HISP Centre developed this reference implementation to demonstrate the electronic transmission of lab results from a LIS to DHIS2, improving the timeliness and quality of such results in Tracker programmes.

As defined in [Laboratory Information Systems Project Management: A Guidebook for International Implementations](https://aphl.org/docs/default-source/technical/gh-2019may-lis-guidebook-web.pdf), a LIS is a _computer-based information management systems created specifically for laboratories, to support workflow, track data from the start to the end of the testing process, store data, and provide correct and complete information to laboratory staff, managers, and customers in a timely manner allowing for decision making by clinicians, epidemiologists and other stakeholders_.

This reference implementation imports the lab results from a LIS into a DHIS2 Tracker programme used for case-based disease surveillance. The import is accomplished by (1) fetching lab diagnostic reports from a mock LIS conforming to the HL7 Laboratory FHIR Implementation Guide, (2) transforming them into Tracker events, and then (3) transmitting the events to DHIS2. The data exchange between the health information systems is mediated thanks to a DHIS2-driven interoperability layer component which also bridges the structural and semantic differences between FHIR and DHIS2.

This is an example meant to technically guide you in developing your own integration between an LIS and DHIS2. It **SHOULD NOT** be used directly in production without adapting it to your local context.

## Quick Start

1. From the machine where you intend to run the reference implementation:
   1. [Install Docker Desktop](https://docs.docker.com/desktop/) which provides the tooling required to bring up the sandbox environment
   2. [Install the Git client](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git) and then run the command shown next to download the reference implementation repository: `git clone https://github.com/dhis2/reference-dhis2-tracker-lis-integration.git`
   3. [Install the Bruno script runner](https://docs.usebruno.com/bru-cli/installation) for simulating the lab analyser that sends the diagnostic results to the LIS
2. Within a terminal, change the current directory to `reference-dhis2-tracker-lab-result-integration` and run `docker compose up --wait --remove-orphans`. Wait until the command completes before moving on to the next step. This command will stand up:
   * DHIS2 which is reachable from `http://localhost:8080/`
   * a mock LIS which is reachable from `http://localhost:8081/`
   * the interoperability layer running as a background process
3. From your browser, type the following in the address bar to open the enrollment form for the case surveillance program: http://localhost:8080/apps/capture#/new?orgUnitId=DiszpKrYNg8&programId=N07iEegH3Hw. Alternatively, follow these steps:
   1. Open the Capture app from the DHIS2 dashboard in your local DHIS2 instance on `http://localhost:8080/`
   2. Expand the _Program_ drop-down box and pick _Case Surveillance_ 
   3. Expand the _Organisation unit_ down-down box and type _Ngelehun CHC_ before proceeding to select it
   4. Press the _Create new person_ button
4. In the enrollment form, expand the _Initial Diagnosis_ drop-down box and pick `Ebola`
5. Press the _Save person_ button, located at the bottom of the form
6. From the enrollment dashboard, click on _New Lab request event_
   1. Choose a date from the _Date of data entry_ date picker
   2. Insert a random identifier like `123456` in the _Specimen ID_ field (the specimen ID must always be unique across all lab request events)
   3. Press the `Complete` button, located at bottom of the form
7. From a terminal, change the current directory to `reference-dhis2-tracker-lab-result-integration/tests/create-fake-lab-diagnostic-report-collection` and launch `bru run` to simulate the lab analyser. Wait until the command completes before moving on to the next step.
8. Wait at least a minute before refreshing the DHIS2 enrollment dashboard in order to give time for the LIS lab result to be synced with DHIS2. After the refresh, an event should appear under the _Lab result_ section of the enrollment dashboard but try refreshing the page a couple of more times if the event does not show up.
9. Open the lab result event to view the lab diagnosis confirming or refuting the initial Ebola diagnosis.

## Overview

The following diagram conceptualises the architecture of this reference implementation:

![Architecture](docs/ref-dhis2-tracker-lis-integration.png)

What follows is a brief overview of the architectural components:

### DHIS2

The role assigned to DHIS2 in this reference implementation is that of an [integrated surveillance and outbreak response system](https://dhis2.org/events/africa-cdc-toolkit-ebola/). The DHIS2 instance is preconfigured with programs covering case surveillance and contract tracing, nonetheless, the lab result integration is focused on the case surveillance program. The workflow of this program is depicted below:

![Case surveillance program](docs/case-surveillance-program.png)

The following sections drill down into the stages which are relevant to the lab result integration.

#### Enrollment Stage

A disease surveillance case in DHIS2 starts with enrollment of a person having a suspect disease. The surveillance officer needs to select the initial diagnosis before they can enroll the person into the programme. As shown below, the initial diagnosis can be either Cholera, Ebola, or Mpox:



#### Lab Request Stage

The lab request stage is used for reporting the lab order and to link the LIS lab result to the surveillance case. The link is established thanks to the specimen ID which is entered into this stage's data entry form shown next:

![Lab request form](docs/lab-request-form.png)

The specimen ID field shown above is mandatory as is expected to be unique for each lab request, even across cases. In other settings, instead of the specimen ID, alternative or additional unique linking identifiers could be required such as the patient name or the case ID, each with their own tradeoffs.

Completing the lab request form does not trigger a lab order. It is assumed that the lab test itself is ordered at a prior point in the overall disease surveillance workflow (e.g., during initial clinical diagnosis). However, to facilitate testing and demoing, accompanying the reference implementation is a test kit that fetches the completed lab requests of in-progress cases from DHIS2, generates corresponding fake lab reports, and pushes the reports to the LIS.

#### Lab Result Stage

Following the lab request is the lab result program stage. This is the stage that integrates with the LIS through the Interoperability Layer as described in the next section. Automatically, a lab result is imported into the ongoing case when a lab report becomes available from the LIS and has a specimen ID linking it to a lab request in DHIS2. The outcome is a completed lab result data entry form like the following:

![Lab result form](docs/lab-result-form.png)

From the Capture app, the surveillance officer enrols a suspected person of a notifiable disease into the _Case Surveillance_ program. On enrolling a person with an initial diagnosis, such as Ebola, the surveillance officer proceeds to the lab request program stage to issue a lab order so that the subsequent test result confirms the initial diagnosis.

The lab request program stage requires at minimum the specimen ID. This ID is the unique identifier allowing the corresponding LIS lab report to be linked to the DHIS2 lab result program stage. In many settings, instead of the specimen ID, alternative or additional unique identifiers could be required such as the patient name or the case ID, each with their own tradeoffs. The specimen ID allows us to handle situations 

When the lab request stage is completed, the IOL picks up the lab request and attempts to reconcile it with a diagnostic report from the LIS.

---

As part of the lab result integration, DHIS2 drives the transformation and terminology mapping such that the lab result can be imported into DHIS2. In terms of FHIR-to-DHIS2 JSON transformation, the DHIS2 data store holds the script translating the FHIR resources into DHIS2 resources:



While in terms of terminology mapping, DHIS2 binds the data elements and option set values to lab terminology via attributes. The the Interoperability Layer section of this documentation explains ....

### Lab Information System

The LIS is the source of the lab results in the DHIS2 case surveillance programme. In the real world, one or more lab analysers would run tests on the specimen and then report their results to the LIS for storage and analysis. However, in this reference implementation, a script runner is used instead to fake the results and transmit them to the LIS. These results are in turn read by the Interoperability Layer as described in the next section.

HAPI FHIR is the server powering the LIS. It is an open-source FHIR server that allows us to keep the integration decoupled from any particular LIS interface. FHIR (Fast Healthcare Interoperability Resources) is a modern, adaptable health data exchange standard. The HAPI FHIR server is configured to conform to the [universal Laboratory Report Implementation Guide](https://build.fhir.org/ig/HL7/uv-lab-rep-ig/). At the time of writing, the guide is still in draft stage, nevertheless, it was selected to represent the lab result communication due to its broad scope thanks to the participation of experts from several countries, projects, and initiatives. The IG profiles several resources, though for the purposes of this project, the follow FHIR resources are of primary interest:

* Specimen: holds the specimen ID and the date the specimen was received at the lab
* Observation: contains the LOINC codes identifying the test carried out and its result
* Patient: the test subject which should anonymous in order to respect the patient's privacy
* DiagnosticReport: brings together the specimen, the observation, and the anonymous patient resources.

### Interoperability Layer

The interoperability layer (IOL) is a low-code and customisable Apache Camel application that bridges the LIS's diagnostic report to DHIS2's lab result program stage. The application routinely scans DHIS2 for lab requests in active case surveillance enrollments. The IOL pulls out the specimen IDs from these lab requests and then searches for FHIR diagnostic reports in the LIS that match these specimen IDs.

Prior to transmitting the lab result to DHIS2, the IOL transforms the FHIR diagnostic report together with its linked FHIR observations and specimen resources into a DHIS2 event resource. However, it is DHIS2 itself that drives the JSON transformation and the terminology mapping. This is thanks to the DHIS2 data store and metadata attributes. The data store key `iol/diagnosticReportMap` holds the DataSonnet script that is fetched and executed in the IOL to translate the FHIR JSON into DHIS2 JSON while the data element and option values attributes hold the LOINC codes permitting the LOINC terminology to be mapped to DHIS2 data elements and their values (e.g., 75411-9 -> CS_LAB_RT_P..). At the start of each poll, the IOL fetches the DataSonnet script to execute in the engine and the terminology mappings to apply to the LOINC codes. The DHIS2 implementer benefits from this separation of logic because it is transformation and mapping logic that is most likely to change over rtime. The DHIS2 implementer can revise the LOINC-to-DHIS2 code mappings without needing to enlist the team maintaing the IOL. A step futher, haivng proficiency in DataSonnet enables the DHIS2 implementer


|           **Parameter Name**            | **Description**                                                                                                   |
|:---------------------------------------:|:------------------------------------------------------------------------------------------------------------------|
|              dhis2.api.url              | Web API base path of the DHIS2 server                                                                             |
|           dhis2.api.username            | Username of the DHIS2 Web API user. Required when not using PAT authentication                                    |
|           dhis2.api.password            | Password of the DHIS2 Web API user. Required when not using PAT authentication                                    |
|              dhis2.api.pat              | PAT of the DHIS2 server Web API user. Required when not using basic access authentication                         |
|         dhis2.api.readTimeoutMs         | Base URL used in approval links sent to the target. If unset, the app will attempt to resolve the base URL itself |
|      dhis2.loincCodesAttribute.id       | Base URL used in approval links sent to the target. If unset, the app will attempt to resolve the base URL itself |
|            dhis2.program.id             | Address for connecting to the broker                                                                              |
|  dhis2.program.specimenDataElement.id   | Reference to a connection factory Java class used for establishing connections to the broker                      |
| dhis2.program.labRequestProgramStage.id | Specifies the Camel runtime version                                                                               |
| dhis2.program.labResultProgramStage.id  | Specifies the Camel runtime version                                                                               |
|               lis.api.url               | Base URL used in approval links sent to the target. If unset, the app will attempt to resolve the base URL itself |


## Performance Considerations

* The no. of requests the LIS receives is pr 

