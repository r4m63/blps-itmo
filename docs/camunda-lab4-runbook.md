# Camunda Lab 4 Runbook

## What Changed

`claim-service` now embeds Camunda 7 in the Spring Boot web application.

The executable process model is:

```text
services/claim-service/src/main/resources/processes/claim-process.bpmn
```

The process replaces the previous static claim state machine and Temporal saga:

- human steps are Camunda user tasks with generated task forms;
- role routing is modeled with `camunda:candidateGroups`;
- claim DB changes and outbox writes are still local `@Transactional` actions;
- Kafka consumers correlate downstream events into Camunda message events;
- business timeouts are BPMN timer events;
- penalty compensation is a BPMN branch, not Temporal code.

## Camunda UI

After starting `claim-service`, open:

```text
http://localhost:8081/camunda
```

Default local admin:

```text
login: admin
password: admin
```

Useful apps:

- Tasklist: user tasks and generated forms.
- Cockpit: process instances, BPMN path, variables, incidents.

## Local Start

Start infrastructure:

```bash
docker compose up -d kafka kafka-ui minio postgres-auth postgres-claim postgres-penalty jira
```

Start services:

```bash
./gradlew :auth-service:bootRun
./gradlew :claim-service:bootRun
./gradlew :penalty-service:bootRun
```

Camunda engine tables are created in `blps_claim` by:

```yaml
camunda.bpm.database.schema-update: true
```

For manual saga-state checks you can shorten BPMN timers:

```bash
./gradlew :claim-service:bootRun --console=plain --args="\
--claim-process.timers.started-hold=PT8S \
--claim-process.timers.awaiting-penalty-applied=PT10S \
--claim-process.timers.awaiting-penalty-counted=PT10S \
--claim-process.timers.compensating-retry=PT5S"
```

## Business Process Mapping

| BPMN element | Previous static code |
|---|---|
| `task_intake_start` | `ClaimService.startIntake` |
| `task_intake_decision` | `ClaimService.intakeDecision` |
| `task_additional_info` | `ClaimService.provideAdditionalInfo` |
| `task_assessment` | `ClaimService.assess` |
| `task_tenant_response` + boundary timer | tenant response endpoint + timeout job |
| `task_support_decision` | `ClaimService.supportDecision` |
| `PENALTY_APPLIED`, `PENALTY_FAILED`, `PENALTY_COUNTED`, `PENALTY_REVOKED` message events | Temporal workflow signals |
| compensation timers/retry gateway | Temporal durable timers and retry loop |

`claim-process.timers.started-hold` is only for manual testing of `STARTED`.
The BPMN process creates `current_saga_id`, exposes `STARTED`, waits for this
timer, and only then writes `PENALTY_APPLICATION_REQUESTED` to the outbox. This
prevents fast Kafka responses from arriving before the BPMN message catch event
exists.

REST endpoints remain compatible with the previous scenarios. Internally they now complete Camunda tasks instead of directly deciding the next process step.

## Saga State

`/api/sagas/{sagaId}` now reads Camunda runtime/history variables:

```text
sagaState
sagaAttemptCount
sagaMaxAttempts
sagaFailureReason
sagaStartedAt
sagaLastEventAt
sagaCompletedAt
```

`claims.current_saga_id` is still the public correlation id and is also stored as Camunda variable `sagaId`.

## Kafka Correlation

`ClaimEventConsumer` keeps inbox/idempotency checks and then calls `PenaltyApplicationSaga`.

`PenaltyApplicationSaga` now uses Camunda:

```text
runtimeService.createMessageCorrelation(...)
  .processInstanceVariableEquals("sagaId", sagaId)
```

So duplicate Kafka events are still safe: if the BPMN process already moved past a message catch event, correlation is ignored.

## WAR For Tomcat

Build:

```bash
./gradlew :claim-service:bootWar
```

Artifact:

```text
services/claim-service/build/libs/claim-service.war
```

Deploy to Tomcat on helios:

```bash
scp services/claim-service/build/libs/claim-service.war <user>@helios:<tomcat>/webapps/claim-service.war
```

The app class extends `SpringBootServletInitializer`, so the same code can run as `bootRun` locally and as a WAR in external Tomcat.
