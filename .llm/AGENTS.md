# BLPS Lab3 Context For Agents

## What this repo is

This repository is the `lab3` version of the project: an event-driven Spring Boot multi-module system with Kafka and separate Postgres databases per service.

Current modules:

- [platform-core](../lib/platform-core)
- [grpc-contracts](../lib/grpc-contracts)
- [api-gateway](../services/api-gateway)
- [auth-service](../services/auth-service)
- [claim-service](../services/claim-service)
- [assessment-service](../services/assessment-service)
- [penalty-service](../services/penalty-service)
- [storage-service](../services/storage-service)
- [notification-service](../services/notification-service)
- [audit-service](../services/audit-service)

Not implemented in the current codebase:

- workflow engine execution of BPMN

Do not reason from old monolith assumptions. The root `src/` and legacy SQL/scenario files were removed.

## Runtime topology

Infrastructure is defined in [docker-compose.yml](../docker-compose.yml).

Current runtime components:

- ZooKeeper
- Kafka
- Kafka UI
- `postgres-auth`
- `postgres-claim`
- `postgres-assessment`
- `postgres-penalty`
- `postgres-storage`
- `postgres-notification`
- `postgres-audit`

Environment template:

- [.env.sample](../.env.sample)

Build structure:

- [settings.gradle.kts](../settings.gradle.kts)
- [build.gradle.kts](../build.gradle.kts)

## Architectural truth

This lab no longer uses XA/JTA across multiple databases.

Distributed consistency is implemented with:

- local DB transaction per service
- `outbox_events`
- `processed_messages`
- Kafka topics
- DLT topics through the shared Kafka error handler
- idempotent consumers
- explicit intermediate statuses

Primary implementation files:

- [platform-core/src/main/java/blps/itmo/platform/persistence/OutboxService.java](../lib/platform-core/src/main/java/blps/itmo/platform/persistence/OutboxService.java)
- [platform-core/src/main/java/blps/itmo/platform/persistence/ProcessedMessageService.java](../lib/platform-core/src/main/java/blps/itmo/platform/persistence/ProcessedMessageService.java)
- [platform-core/src/main/java/blps/itmo/platform/persistence/OutboxRelay.java](../lib/platform-core/src/main/java/blps/itmo/platform/persistence/OutboxRelay.java)
- [platform-core/src/main/java/blps/itmo/platform/kafka/PlatformKafkaConfig.java](../lib/platform-core/src/main/java/blps/itmo/platform/kafka/PlatformKafkaConfig.java)
- [platform-core/src/main/java/blps/itmo/platform/grpc/GrpcServerLifecycle.java](../lib/platform-core/src/main/java/blps/itmo/platform/grpc/GrpcServerLifecycle.java)
- [platform-core/src/main/java/blps/itmo/platform/events/EventType.java](../lib/platform-core/src/main/java/blps/itmo/platform/events/EventType.java)
- [platform-core/src/main/java/blps/itmo/platform/events/TopicNames.java](../lib/platform-core/src/main/java/blps/itmo/platform/events/TopicNames.java)
- [platform-core/src/main/java/blps/itmo/platform/security/DemoJwtService.java](../lib/platform-core/src/main/java/blps/itmo/platform/security/DemoJwtService.java)
- [grpc-contracts/src/main/proto](../lib/grpc-contracts/src/main/proto)

Never re-introduce cross-service direct DB access.

Communication rules:

- external clients call only `api-gateway` over HTTP under `/api/**`
- synchronous calls inside the system use gRPC contracts from `lib/grpc-contracts`
- async business workflows use Kafka events published through outbox relays
- backend REST controllers may exist for direct local debug, but they are not the service-to-service contract

## Service ownership

### `api-gateway`

Owns:

- external routing for `/api/**`
- demo JWT validation
- `X-User-Id`, `X-User-Role`, and `X-Correlation-Id` propagation

Main files:

- [ApiGatewayApplication.java](../services/api-gateway/src/main/java/blps/itmo/gateway/ApiGatewayApplication.java)
- [GatewayAuthFilter.java](../services/api-gateway/src/main/java/blps/itmo/gateway/GatewayAuthFilter.java)
- [GatewayHttpController.java](../services/api-gateway/src/main/java/blps/itmo/gateway/GatewayHttpController.java)
- [GatewayGrpcClients.java](../services/api-gateway/src/main/java/blps/itmo/gateway/GatewayGrpcClients.java)
- [GatewayExceptionHandler.java](../services/api-gateway/src/main/java/blps/itmo/gateway/GatewayExceptionHandler.java)

### `auth-service`

Owns:

- users
- roles
- enabled/disabled flag
- `penaltyCount`

Main files:

- [AuthController.java](../services/auth-service/src/main/java/blps/itmo/auth/controller/AuthController.java)
- [AuthGrpcService.java](../services/auth-service/src/main/java/blps/itmo/auth/grpc/AuthGrpcService.java)
- [AuthUserService.java](../services/auth-service/src/main/java/blps/itmo/auth/service/AuthUserService.java)
- [User.java](../services/auth-service/src/main/java/blps/itmo/auth/domain/User.java)

Important behavior:

- seeds 6 demo users on startup
- exposes `/api/auth/login` and issues demo JWTs
- exposes `AuthRpcService` for gateway and synchronous user lookup
- emits `USER_DEACTIVATED`
- consumes `PENALTY_APPLIED` and increments `penaltyCount`

### `claim-service`

Owns:

- claim aggregate
- claim status machine
- claim timeline
- orchestration of the claim lifecycle through events

Main files:

- [ClaimController.java](../services/claim-service/src/main/java/blps/itmo/claim/controller/ClaimController.java)
- [ClaimGrpcService.java](../services/claim-service/src/main/java/blps/itmo/claim/grpc/ClaimGrpcService.java)
- [ClaimProcessService.java](../services/claim-service/src/main/java/blps/itmo/claim/service/ClaimProcessService.java)
- [ClaimStatus.java](../services/claim-service/src/main/java/blps/itmo/claim/domain/ClaimStatus.java)
- [ClaimEventListeners.java](../services/claim-service/src/main/java/blps/itmo/claim/service/ClaimEventListeners.java)
- [AuthClient.java](../services/claim-service/src/main/java/blps/itmo/claim/client/AuthClient.java)

Public endpoints:

- `POST /api/claims`
- `GET /api/claims/{id}`
- `GET /api/claims/{id}/process-status`
- `GET /api/claims/{id}/timeline`
- `GET /api/claims/{id}/attachments`
- `POST /api/claims/{id}/additional-info`
- `POST /api/claims/{id}/tenant-response`
- `POST /api/claims/{id}/support-decision`
- `POST /api/claims/{id}/repair/reassess`
- `POST /api/claims/{id}/repair/close`

### `assessment-service`

Owns:

- async assessment jobs
- simple assessment heuristic

Main files:

- [AssessmentWorkflowService.java](../services/assessment-service/src/main/java/blps/itmo/assessment/service/AssessmentWorkflowService.java)
- [AssessmentJob.java](../services/assessment-service/src/main/java/blps/itmo/assessment/domain/AssessmentJob.java)

Consumes:

- `CLAIM_CREATED`
- `ADDITIONAL_INFO_PROVIDED`

Produces:

- `ASSESSMENT_COMPLETED`
- `ASSESSMENT_FAILED`

### `penalty-service`

Owns:

- async penalty operations
- retry of failed penalty processing

Main files:

- [PenaltyWorkflowService.java](../services/penalty-service/src/main/java/blps/itmo/penalty/service/PenaltyWorkflowService.java)
- [PenaltyController.java](../services/penalty-service/src/main/java/blps/itmo/penalty/service/PenaltyController.java)
- [PenaltyGrpcService.java](../services/penalty-service/src/main/java/blps/itmo/penalty/grpc/PenaltyGrpcService.java)
- [PenaltyOperation.java](../services/penalty-service/src/main/java/blps/itmo/penalty/domain/PenaltyOperation.java)

Consumes:

- `PENALTY_APPLICATION_REQUESTED`

Produces:

- `PENALTY_APPLIED`
- `PENALTY_APPLICATION_FAILED`

### `storage-service`

Owns:

- attachment metadata
- MinIO object key lifecycle
- attachment binding saga state

Main files:

- [AttachmentController.java](../services/storage-service/src/main/java/blps/itmo/storage/controller/AttachmentController.java)
- [StorageGrpcService.java](../services/storage-service/src/main/java/blps/itmo/storage/grpc/StorageGrpcService.java)
- [StorageWorkflowService.java](../services/storage-service/src/main/java/blps/itmo/storage/service/StorageWorkflowService.java)
- [Attachment.java](../services/storage-service/src/main/java/blps/itmo/storage/domain/Attachment.java)

Consumes:

- `ATTACHMENT_BINDING_REQUESTED`

Produces:

- `ATTACHMENT_INITIALIZED`
- `ATTACHMENT_CONFIRMED`
- `ATTACHMENT_BOUND`
- `ATTACHMENT_BINDING_FAILED`

### `notification-service`

Owns:

- notification log only

Main files:

- [NotificationEventListener.java](../services/notification-service/src/main/java/blps/itmo/notification/service/NotificationEventListener.java)
- [NotificationLog.java](../services/notification-service/src/main/java/blps/itmo/notification/domain/NotificationLog.java)

### `audit-service`

Owns:

- immutable event trail for demo and inspection

Main files:

- [AuditEventListener.java](../services/audit-service/src/main/java/blps/itmo/audit/service/AuditEventListener.java)
- [AuditController.java](../services/audit-service/src/main/java/blps/itmo/audit/controller/AuditController.java)
- [AuditGrpcService.java](../services/audit-service/src/main/java/blps/itmo/audit/grpc/AuditGrpcService.java)
- [AuditRecord.java](../services/audit-service/src/main/java/blps/itmo/audit/domain/AuditRecord.java)

## Current business flow

The BPMN in [bpmn/blps1.bpmn](../docs/bpmn/blps1.bpmn) is a business reference, not an executable workflow.

The current runtime flow is:

1. client logs in through `api-gateway` and receives a demo JWT
2. landlord creates claim through `api-gateway`
3. claim is stored with status `ASSESSMENT_IN_PROGRESS`
4. `claim-service` writes `CLAIM_CREATED` to outbox
5. optional attachment refs are bound through `storage-service`
6. `assessment-service` creates an async job
7. assessment emits `ASSESSMENT_COMPLETED`
8. `claim-service` moves the claim:
   - to `NEED_ADDITIONAL_INFO` if materials are insufficient
   - to `AWAITING_TENANT_RESPONSE` if penalty grounds exist
   - to `CLOSED_NO_PENALTY` if grounds do not exist
9. landlord may send additional info
10. tenant may respond
11. admin makes final support decision
12. if penalty is requested, claim moves to `PENALTY_PROCESSING`
13. `penalty-service` asynchronously applies or fails the penalty
14. `auth-service` reacts to `PENALTY_APPLIED`
15. `notification-service` and `audit-service` consume the same events independently

## Implemented business rules

Assessment logic is intentionally simple and code-driven, not BPMN-driven:

- on first assessment attempt, `claimedAmount >= 200` leads to `requiresAdditionalInfo=true`
- otherwise `penaltyGrounds = claimedAmount >= 50`
- when grounds exist, `assessmentAmount = claimedAmount * 0.70`

Penalty logic:

- `support-decision` with `applyPenalty=false` closes claim immediately as `CLOSED_NO_PENALTY`
- `support-decision` with `applyPenalty=true` requires a positive `penaltyAmount`
- `simulateFailure=true` forces `penalty-service` into the failure path
- failed penalty processing can be retried through `POST /api/penalties/operations/{operationId}/retry`

User deactivation logic:

- `auth-service` emits `USER_DEACTIVATED`
- `claim-service` closes open claims of that user asynchronously

## Claim statuses

Source of truth:

- [ClaimStatus.java](../services/claim-service/src/main/java/blps/itmo/claim/domain/ClaimStatus.java)
- [init_claim_service.sql](../sql/init_claim_service.sql)

Current statuses:

- `ASSESSMENT_IN_PROGRESS`
- `ASSESSMENT_FAILED`
- `MANUAL_REVIEW_REQUIRED`
- `NEED_ADDITIONAL_INFO`
- `AWAITING_TENANT_RESPONSE`
- `SUPPORT_REVIEW`
- `PENALTY_PROCESSING`
- `PENALTY_APPLIED`
- `PENALTY_PROCESSING_FAILED`
- `CLOSED_NO_PENALTY`

If you change the lifecycle, update together:

- Java enum
- service transition logic
- SQL enum in claim DB schema
- relevant `.http` demo scenarios

## Event contracts

Source of truth:

- [EventType.java](../lib/platform-core/src/main/java/blps/itmo/platform/events/EventType.java)
- [payload package](../lib/platform-core/src/main/java/blps/itmo/platform/events/payload)

Current event set:

- `CLAIM_CREATED`
- `ADDITIONAL_INFO_PROVIDED`
- `ASSESSMENT_COMPLETED`
- `ASSESSMENT_FAILED`
- `TENANT_RESPONSE_RECEIVED`
- `TENANT_RESPONSE_EXPIRED`
- `CLAIM_CLOSED_NO_PENALTY`
- `PENALTY_APPLICATION_REQUESTED`
- `PENALTY_APPLIED`
- `PENALTY_APPLICATION_FAILED`
- `USER_DEACTIVATED`
- `ATTACHMENT_INITIALIZED`
- `ATTACHMENT_CONFIRMED`
- `ATTACHMENT_BINDING_REQUESTED`
- `ATTACHMENT_BOUND`
- `ATTACHMENT_BINDING_FAILED`

Use `claimId` or `userId` as the natural aggregate key when evolving flows.

## SQL schemas

Per-service schema files live in `sql/`:

- [init_auth_service.sql](../sql/init_auth_service.sql)
- [init_claim_service.sql](../sql/init_claim_service.sql)
- [init_assessment_service.sql](../sql/init_assessment_service.sql)
- [init_penalty_service.sql](../sql/init_penalty_service.sql)
- [init_storage_service.sql](../sql/init_storage_service.sql)
- [init_notification_service.sql](../sql/init_notification_service.sql)
- [init_audit_service.sql](../sql/init_audit_service.sql)

Matching drop scripts exist for each service.

All service schemas include local `outbox_events` and `processed_messages`.

## Manual verification

Primary runbook:

- [docs/lab3-runbook.md](../docs/lab3-runbook.md)

Primary HTTP scenarios:

- [30-lab3-async-penalty.http](../rest-client/scenarios/30-lab3-async-penalty.http)
- [31-lab3-penalty-failure-recovery.http](../rest-client/scenarios/31-lab3-penalty-failure-recovery.http)
- [32-lab3-user-deactivation.http](../rest-client/scenarios/32-lab3-user-deactivation.http)
- [33-lab3-attachment-saga.http](../rest-client/scenarios/33-lab3-attachment-saga.http)

## Practical guardrails

- Do not recreate the deleted monolith structure.
- Prefer current service code over planning documents when they disagree.
- Treat `.llm/lab3.md` and `.llm/distribution-transaction.md` as design docs, not as proof that a component already exists.
- `auth-service` issues demo JWTs, while `api-gateway` validates them and propagates actor headers.
- `notification-service` and `audit-service` are read-side consumers; keep their handlers idempotent.
- If you add a new event, update event enum, payload, topic routing, producer, consumer, SQL schemas if needed, and at least one demo scenario.
