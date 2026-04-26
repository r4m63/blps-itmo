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
6. [platform-core](../lib/platform-core)
7. service `Application`/`Controller`/`Service` files in the affected modules
8. matching `sql/init_*_service.sql`

Output focus:

- which service owns the data
- whether the flow is external HTTP, internal sync gRPC, or async Kafka
- which event starts and completes the flow
- which DB schema must stay aligned

## Skill: Claim Lifecycle Change

Use when changing statuses, claim validation, timeline, or orchestration logic.

Primary files:

- [ClaimProcessService.java](../services/claim-service/src/main/java/blps/itmo/claim/service/ClaimProcessService.java)
- [ClaimController.java](../services/claim-service/src/main/java/blps/itmo/claim/controller/ClaimController.java)
- [ClaimStatus.java](../services/claim-service/src/main/java/blps/itmo/claim/domain/ClaimStatus.java)
- [ClaimEventListeners.java](../services/claim-service/src/main/java/blps/itmo/claim/service/ClaimEventListeners.java)
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

- [EventType.java](../lib/platform-core/src/main/java/blps/itmo/platform/events/EventType.java)
- [TopicNames.java](../lib/platform-core/src/main/java/blps/itmo/platform/events/TopicNames.java)
- [payload package](../lib/platform-core/src/main/java/blps/itmo/platform/events/payload)
- [OutboxService.java](../lib/platform-core/src/main/java/blps/itmo/platform/persistence/OutboxService.java)
- [ProcessedMessageService.java](../lib/platform-core/src/main/java/blps/itmo/platform/persistence/ProcessedMessageService.java)
- [OutboxRelay.java](../lib/platform-core/src/main/java/blps/itmo/platform/persistence/OutboxRelay.java)
- [PlatformKafkaConfig.java](../lib/platform-core/src/main/java/blps/itmo/platform/kafka/PlatformKafkaConfig.java)

Checklist:

- keep business write and outbox write in one local transaction
- keep consumers idempotent through `processed_messages`
- do not introduce direct DB reads across services
- update every producer and consumer impacted by the event
- keep SQL schemas aligned if outbox/inbox structure changes
- update at least one demo flow that proves the event is used

## Skill: Gateway Or Security Change

Use when changing external routing, login, JWT claims, or actor header propagation.

Primary files:

- [GatewayAuthFilter.java](../services/api-gateway/src/main/java/blps/itmo/gateway/GatewayAuthFilter.java)
- [GatewayHttpController.java](../services/api-gateway/src/main/java/blps/itmo/gateway/GatewayHttpController.java)
- [GatewayGrpcClients.java](../services/api-gateway/src/main/java/blps/itmo/gateway/GatewayGrpcClients.java)
- [GatewayExceptionHandler.java](../services/api-gateway/src/main/java/blps/itmo/gateway/GatewayExceptionHandler.java)
- [grpc-contracts/src/main/proto](../lib/grpc-contracts/src/main/proto)
- [DemoJwtService.java](../lib/platform-core/src/main/java/blps/itmo/platform/security/DemoJwtService.java)
- [AuthController.java](../services/auth-service/src/main/java/blps/itmo/auth/controller/AuthController.java)
- [AuthUserService.java](../services/auth-service/src/main/java/blps/itmo/auth/service/AuthUserService.java)

Checklist:

- keep `/api/auth/login` public
- keep `/internal/**` out of gateway routing
- propagate `X-User-Id`, `X-User-Role`, and `X-Correlation-Id`
- keep gateway as HTTP edge and call backend services through gRPC stubs
- update `.http` scenarios when external request shape changes

## Skill: Storage Attachment Saga Change

Use when changing attachment init/confirm/bind behavior or MinIO metadata ownership.

Primary files:

- [AttachmentController.java](../services/storage-service/src/main/java/blps/itmo/storage/controller/AttachmentController.java)
- [StorageGrpcService.java](../services/storage-service/src/main/java/blps/itmo/storage/grpc/StorageGrpcService.java)
- [StorageWorkflowService.java](../services/storage-service/src/main/java/blps/itmo/storage/service/StorageWorkflowService.java)
- [Attachment.java](../services/storage-service/src/main/java/blps/itmo/storage/domain/Attachment.java)
- [ClaimProcessService.java](../services/claim-service/src/main/java/blps/itmo/claim/service/ClaimProcessService.java)
- [init_storage_service.sql](../sql/init_storage_service.sql)
- [33-lab3-attachment-saga.http](../rest-client/scenarios/33-lab3-attachment-saga.http)

Checklist:

- keep MinIO object metadata owned by `storage-service`
- keep claim-side data as attachment refs/read model only
- use `ATTACHMENT_BINDING_REQUESTED -> ATTACHMENT_BOUND/FAILED`
- preserve idempotency through `processed_messages`

## Skill: Assessment Rule Change

Use when the claim assessment heuristic or async job behavior changes.

Primary files:

- [AssessmentWorkflowService.java](../services/assessment-service/src/main/java/blps/itmo/assessment/service/AssessmentWorkflowService.java)
- [AssessmentJob.java](../services/assessment-service/src/main/java/blps/itmo/assessment/domain/AssessmentJob.java)
- [ClaimEventListener.java](../services/assessment-service/src/main/java/blps/itmo/assessment/service/ClaimEventListener.java)
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

- [PenaltyWorkflowService.java](../services/penalty-service/src/main/java/blps/itmo/penalty/service/PenaltyWorkflowService.java)
- [PenaltyController.java](../services/penalty-service/src/main/java/blps/itmo/penalty/service/PenaltyController.java)
- [PenaltyOperation.java](../services/penalty-service/src/main/java/blps/itmo/penalty/domain/PenaltyOperation.java)
- [ClaimProcessService.java](../services/claim-service/src/main/java/blps/itmo/claim/service/ClaimProcessService.java)
- [AuthUserService.java](../services/auth-service/src/main/java/blps/itmo/auth/service/AuthUserService.java)
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

- [AuthController.java](../services/auth-service/src/main/java/blps/itmo/auth/controller/AuthController.java)
- [AuthGrpcService.java](../services/auth-service/src/main/java/blps/itmo/auth/grpc/AuthGrpcService.java)
- [auth.proto](../lib/grpc-contracts/src/main/proto/auth.proto)
- [AuthUserService.java](../services/auth-service/src/main/java/blps/itmo/auth/service/AuthUserService.java)
- [User.java](../services/auth-service/src/main/java/blps/itmo/auth/domain/User.java)
- [UserRole.java](../services/auth-service/src/main/java/blps/itmo/auth/domain/UserRole.java)
- [init_auth_service.sql](../sql/init_auth_service.sql)
- [32-lab3-user-deactivation.http](../rest-client/scenarios/32-lab3-user-deactivation.http)
- [33-lab3-attachment-saga.http](../rest-client/scenarios/33-lab3-attachment-saga.http)

Checklist:

- if user schema changes, update both seed logic and SQL
- keep `AuthRpcService.GetUser` stable unless every gRPC caller is updated
- deactivation must still emit `USER_DEACTIVATED`
- claim cleanup on deactivation belongs in `claim-service`, not `auth-service`

## Skill: Read-Side Consumer Change

Use when changing `notification-service` or `audit-service`.

Primary files:

- [NotificationEventListener.java](../services/notification-service/src/main/java/blps/itmo/notification/service/NotificationEventListener.java)
- [AuditEventListener.java](../services/audit-service/src/main/java/blps/itmo/audit/service/AuditEventListener.java)
- [AuditController.java](../services/audit-service/src/main/java/blps/itmo/audit/controller/AuditController.java)
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
