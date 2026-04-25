# BLPS Lab3 Skills

Use the smallest relevant playbook. This repository is no longer a monolith.

## Skill: Architecture Sweep

Use when the task spans multiple services or the user asks how the system works now.

Read in this order:

1. [settings.gradle.kts](../settings.gradle.kts)
2. [build.gradle.kts](../build.gradle.kts)
3. [docker-compose.yml](../docker-compose.yml)
4. [.env.sample](../.env.sample)
5. [docs/lab3-runbook.md](../docs/lab3-runbook.md)
6. [platform-core](../platform-core)
7. service `Application`/`Controller`/`Service` files in the affected modules
8. matching `sql/init_*_service.sql`

Output focus:

- which service owns the data
- whether the flow is sync HTTP or async Kafka
- which event starts and completes the flow
- which DB schema must stay aligned

## Skill: Claim Lifecycle Change

Use when changing statuses, claim validation, timeline, or orchestration logic.

Primary files:

- [ClaimProcessService.java](../claim-service/src/main/java/blps/itmo/claim/service/ClaimProcessService.java)
- [ClaimController.java](../claim-service/src/main/java/blps/itmo/claim/controller/ClaimController.java)
- [ClaimStatus.java](../claim-service/src/main/java/blps/itmo/claim/domain/ClaimStatus.java)
- [ClaimEventListeners.java](../claim-service/src/main/java/blps/itmo/claim/service/ClaimEventListeners.java)
- [init_claim_service.sql](../sql/init_claim_service.sql)
- [30-lab3-async-penalty.http](../rest-client/scenarios/30-lab3-async-penalty.http)
- [31-lab3-penalty-failure-recovery.http](../rest-client/scenarios/31-lab3-penalty-failure-recovery.http)

Checklist:

- confirm allowed source status and target status
- update emitted event if lifecycle changes
- update timeline entry writing
- keep landlord/tenant/admin validation aligned with `auth-service`
- keep SQL enum aligned with Java enum
- update scenario files if external behavior changed

## Skill: Event Contract Or Distributed Transaction Change

Use when adding a new event, changing producer/consumer behavior, or touching outbox/inbox logic.

Primary files:

- [EventType.java](../platform-core/src/main/java/blps/itmo/platform/events/EventType.java)
- [TopicNames.java](../platform-core/src/main/java/blps/itmo/platform/events/TopicNames.java)
- [payload package](../platform-core/src/main/java/blps/itmo/platform/events/payload)
- [OutboxService.java](../platform-core/src/main/java/blps/itmo/platform/persistence/OutboxService.java)
- [ProcessedMessageService.java](../platform-core/src/main/java/blps/itmo/platform/persistence/ProcessedMessageService.java)
- [OutboxRelay.java](../platform-core/src/main/java/blps/itmo/platform/persistence/OutboxRelay.java)

Checklist:

- keep business write and outbox write in one local transaction
- keep consumers idempotent through `processed_messages`
- do not introduce direct DB reads across services
- update every producer and consumer impacted by the event
- keep SQL schemas aligned if outbox/inbox structure changes
- update at least one demo flow that proves the event is used

## Skill: Assessment Rule Change

Use when the claim assessment heuristic or async job behavior changes.

Primary files:

- [AssessmentWorkflowService.java](../assessment-service/src/main/java/blps/itmo/assessment/service/AssessmentWorkflowService.java)
- [AssessmentJob.java](../assessment-service/src/main/java/blps/itmo/assessment/domain/AssessmentJob.java)
- [ClaimEventListener.java](../assessment-service/src/main/java/blps/itmo/assessment/service/ClaimEventListener.java)
- [init_assessment_service.sql](../sql/init_assessment_service.sql)
- [30-lab3-async-penalty.http](../rest-client/scenarios/30-lab3-async-penalty.http)

Checklist:

- separate event ingestion from async processing
- keep first-attempt and re-assessment behavior explicit
- preserve idempotency on duplicate `CLAIM_CREATED` or `ADDITIONAL_INFO_PROVIDED`
- verify resulting claim transitions in `claim-service`

## Skill: Penalty Flow Change

Use when changing penalty application, failure handling, or retry behavior.

Primary files:

- [PenaltyWorkflowService.java](../penalty-service/src/main/java/blps/itmo/penalty/service/PenaltyWorkflowService.java)
- [PenaltyController.java](../penalty-service/src/main/java/blps/itmo/penalty/service/PenaltyController.java)
- [PenaltyOperation.java](../penalty-service/src/main/java/blps/itmo/penalty/domain/PenaltyOperation.java)
- [ClaimProcessService.java](../claim-service/src/main/java/blps/itmo/claim/service/ClaimProcessService.java)
- [AuthUserService.java](../auth-service/src/main/java/blps/itmo/auth/service/AuthUserService.java)
- [init_penalty_service.sql](../sql/init_penalty_service.sql)
- [31-lab3-penalty-failure-recovery.http](../rest-client/scenarios/31-lab3-penalty-failure-recovery.http)

Checklist:

- keep `PENALTY_PROCESSING` as an explicit intermediate state
- emit success and failure events from `penalty-service`, not from `claim-service`
- keep retry flow idempotent
- confirm `auth-service` still reacts correctly to `PENALTY_APPLIED`

## Skill: Auth Or User Deactivation Change

Use when changing demo users, roles, internal user lookup, or deactivation behavior.

Primary files:

- [AuthController.java](../auth-service/src/main/java/blps/itmo/auth/controller/AuthController.java)
- [AuthUserService.java](../auth-service/src/main/java/blps/itmo/auth/service/AuthUserService.java)
- [User.java](../auth-service/src/main/java/blps/itmo/auth/domain/User.java)
- [UserRole.java](../auth-service/src/main/java/blps/itmo/auth/domain/UserRole.java)
- [init_auth_service.sql](../sql/init_auth_service.sql)
- [32-lab3-user-deactivation.http](../rest-client/scenarios/32-lab3-user-deactivation.http)

Checklist:

- if user schema changes, update both seed logic and SQL
- keep `/internal/users/{id}` contract stable unless every caller is updated
- deactivation must still emit `USER_DEACTIVATED`
- claim cleanup on deactivation belongs in `claim-service`, not `auth-service`

## Skill: Read-Side Consumer Change

Use when changing `notification-service` or `audit-service`.

Primary files:

- [NotificationEventListener.java](../notification-service/src/main/java/blps/itmo/notification/service/NotificationEventListener.java)
- [AuditEventListener.java](../audit-service/src/main/java/blps/itmo/audit/service/AuditEventListener.java)
- [AuditController.java](../audit-service/src/main/java/blps/itmo/audit/controller/AuditController.java)
- [init_notification_service.sql](../sql/init_notification_service.sql)
- [init_audit_service.sql](../sql/init_audit_service.sql)

Checklist:

- keep consumers idempotent
- never let read-side consumers become source-of-truth owners
- preserve event trail quality by keeping `correlationId`, `aggregateType`, and `aggregateId`

## Skill: SQL Schema Sync

Use when entity fields or enums changed.

Primary files:

- matching `domain` entity
- matching `sql/init_*_service.sql`
- matching `sql/drop_*_service.sql`

Checklist:

- one service, one schema file pair
- keep enum values identical between Java and SQL
- preserve indexes used by current query paths
- remember `outbox_events` and `processed_messages` exist in every service DB

## Skill: Scenario-Driven Verification

Use when the user asks what is currently implemented or asks to verify business behavior.

Primary files:

- [rest-client/scenarios/README.md](../rest-client/scenarios/README.md)
- [30-lab3-async-penalty.http](../rest-client/scenarios/30-lab3-async-penalty.http)
- [31-lab3-penalty-failure-recovery.http](../rest-client/scenarios/31-lab3-penalty-failure-recovery.http)
- [32-lab3-user-deactivation.http](../rest-client/scenarios/32-lab3-user-deactivation.http)

What to extract:

- which service receives the initial request
- which event(s) carry the flow forward
- where eventual consistency is visible
- how failure and retry are demonstrated

## Skill: Manual Verification

Use when no automated test covers the change.

Fast path:

1. `docker compose up -d`
2. run affected services with `./gradlew :<module>:bootRun`
3. run the nearest scenario from `rest-client/scenarios`
4. use `./gradlew build` before closing substantial changes

## Skill: Review Mode

Use when the user asks for a review.

Priorities:

- broken claim state transitions
- mismatched event producer/consumer contracts
- lost-idempotency risks
- schema drift between entities and SQL
- stale docs or scenarios that describe components that no longer exist
