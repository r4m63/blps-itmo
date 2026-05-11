# Code Navigation Map

Карта ключевых файлов по сервисам. Используется агентами и разработчиками для быстрой навигации: "куда смотреть, если меняем X". Источник истины — сам код, эта карта обновляется вручную при крупных рефакторингах.

## platform-core (общая инфраструктура)

- `lib/platform-core/src/main/java/blps/itmo/platform/outbox/OutboxService.java` — запись исходящего события в одной транзакции с бизнес-update
- `lib/platform-core/src/main/java/blps/itmo/platform/outbox/OutboxRelay.java` — поллинг `outbox_events` (1000ms) и публикация в Kafka
- `lib/platform-core/src/main/java/blps/itmo/platform/outbox/ProcessedMessageService.java` — Inbox / дедупликация входящих событий
- `lib/platform-core/src/main/java/blps/itmo/platform/kafka/PlatformKafkaConfig.java` — Kafka producer/consumer config + DLT error handler
- `lib/platform-core/src/main/java/blps/itmo/platform/grpc/GrpcServerLifecycle.java` — bootstrap gRPC server в сервисах
- `lib/platform-core/src/main/java/blps/itmo/platform/events/EventType.java` — enum всех доменных событий
- `lib/platform-core/src/main/java/blps/itmo/platform/events/TopicNames.java` — константы Kafka топиков
- `lib/platform-core/src/main/java/blps/itmo/platform/events/payload/` — payload-классы событий
- `lib/platform-core/src/main/java/blps/itmo/platform/security/DemoJwtService.java` — выпуск и валидация demo JWT
- `lib/grpc-contracts/src/main/proto/` — protobuf-контракты внутренних gRPC API

## api-gateway (port 8080)

- `services/api-gateway/src/main/java/blps/itmo/gateway/ApiGatewayApplication.java` — main
- `services/api-gateway/src/main/java/blps/itmo/gateway/GatewayAuthFilter.java` — JWT validation, propagation `X-User-Id`, `X-User-Role`, `X-Correlation-Id`
- `services/api-gateway/src/main/java/blps/itmo/gateway/GatewayHttpController.java` — HTTP → gRPC routing
- `services/api-gateway/src/main/java/blps/itmo/gateway/GatewayGrpcClients.java` — gRPC stub clients для всех backend-сервисов
- `services/api-gateway/src/main/java/blps/itmo/gateway/GatewayExceptionHandler.java` — маппинг gRPC ошибок → HTTP

## auth-service (8082 HTTP / 19082 gRPC)

- `services/auth-service/src/main/java/blps/itmo/auth/controller/AuthController.java` — `/api/auth/login`, `/api/auth/users`, `/deactivate`
- `services/auth-service/src/main/java/blps/itmo/auth/grpc/AuthGrpcService.java` — `AuthRpcService` (Login, ListUsers, GetUser, DeactivateUser)
- `services/auth-service/src/main/java/blps/itmo/auth/service/AuthUserService.java` — бизнес-логика
- `services/auth-service/src/main/java/blps/itmo/auth/domain/User.java`, `UserRole.java`
- `sql/init_auth_service.sql`

Поведение: seedит 6 demo users, выдаёт demo JWT, эмиттит `USER_DEACTIVATED`, консьюмит `PENALTY_APPLIED` и инкрементит `penaltyCount`.

## claim-service (8081 HTTP / 19081 gRPC)

- `services/claim-service/src/main/java/blps/itmo/claim/controller/ClaimController.java` — все `/api/claims/**`
- `services/claim-service/src/main/java/blps/itmo/claim/grpc/ClaimGrpcService.java` — `ClaimRpcService`
- `services/claim-service/src/main/java/blps/itmo/claim/service/ClaimProcessService.java` — **центральная state machine**, переходы статусов, запись timeline
- `services/claim-service/src/main/java/blps/itmo/claim/service/ClaimEventListeners.java` — реакция на `ASSESSMENT_*`, `PENALTY_*`, `USER_DEACTIVATED`, `ATTACHMENT_*`
- `services/claim-service/src/main/java/blps/itmo/claim/domain/ClaimStatus.java` — **enum статусов (source of truth)**
- `services/claim-service/src/main/java/blps/itmo/claim/client/AuthClient.java` — sync gRPC вызов `AuthRpcService.GetUser` для валидации actor'а
- `sql/init_claim_service.sql`

Endpoints: `POST /api/claims`, `GET /api/claims/{id}`, `/timeline`, `/process-status`, `/attachments`, `POST /additional-info`, `/tenant-response`, `/support-decision`, `/repair/reassess`, `/repair/close`.

## assessment-service (8083, async worker, gRPC server отсутствует)

- `services/assessment-service/src/main/java/blps/itmo/assessment/service/AssessmentWorkflowService.java` — эвристика оценки + scheduled worker
- `services/assessment-service/src/main/java/blps/itmo/assessment/service/ClaimEventListener.java` — consumer `CLAIM_CREATED` / `ADDITIONAL_INFO_PROVIDED`
- `services/assessment-service/src/main/java/blps/itmo/assessment/domain/AssessmentJob.java`
- `sql/init_assessment_service.sql`

Worker poll: 2000ms. Producer: `ASSESSMENT_COMPLETED`, `ASSESSMENT_FAILED`.

## penalty-service (8084 / 19084)

- `services/penalty-service/src/main/java/blps/itmo/penalty/service/PenaltyWorkflowService.java` — async применение штрафа + retry
- `services/penalty-service/src/main/java/blps/itmo/penalty/service/PenaltyController.java` — `/api/penalties/claims/{id}`, `/operations/{id}/retry`
- `services/penalty-service/src/main/java/blps/itmo/penalty/grpc/PenaltyGrpcService.java` — `PenaltyRpcService`
- `services/penalty-service/src/main/java/blps/itmo/penalty/domain/PenaltyOperation.java`
- `sql/init_penalty_service.sql`

Worker poll: 2000ms. Producer: `PENALTY_APPLIED`, `PENALTY_APPLICATION_FAILED`. Consumer: `PENALTY_APPLICATION_REQUESTED`.

## storage-service (8087 / 19087)

- `services/storage-service/src/main/java/blps/itmo/storage/controller/AttachmentController.java` — `/api/attachments/init`, `/confirm`, `/{id}`
- `services/storage-service/src/main/java/blps/itmo/storage/grpc/StorageGrpcService.java` — `StorageRpcService`
- `services/storage-service/src/main/java/blps/itmo/storage/service/StorageWorkflowService.java` — saga `init → confirm → bind`
- `services/storage-service/src/main/java/blps/itmo/storage/domain/Attachment.java`
- `sql/init_storage_service.sql`

MinIO-метаданные владеет storage-service, на стороне claim — только refs. `STORAGE_VERIFY_MINIO_OBJECT=false` по умолчанию (demo может подтверждать без HEAD-check).

## notification-service (8085)

- `services/notification-service/src/main/java/blps/itmo/notification/service/NotificationEventListener.java`
- `services/notification-service/src/main/java/blps/itmo/notification/domain/NotificationLog.java`
- `sql/init_notification_service.sql`

Read-side projection. Подписан на все доменные топики. Не источник истины.

## audit-service (8086 / 19086)

- `services/audit-service/src/main/java/blps/itmo/audit/service/AuditEventListener.java`
- `services/audit-service/src/main/java/blps/itmo/audit/controller/AuditController.java` — `/api/audit/claims/{id}/events`
- `services/audit-service/src/main/java/blps/itmo/audit/grpc/AuditGrpcService.java`
- `services/audit-service/src/main/java/blps/itmo/audit/domain/AuditRecord.java`
- `sql/init_audit_service.sql`

Immutable event trail. Подписан на все доменные топики.

## Связанные обновления при типичных изменениях

### Меняешь lifecycle / claim status

Обновить **синхронно**:

1. `services/claim-service/.../domain/ClaimStatus.java` (Java enum)
2. `services/claim-service/.../service/ClaimProcessService.java` (transitions, валидация)
3. `services/claim-service/.../service/ClaimEventListeners.java` (если приходит новое событие)
4. `sql/init_claim_service.sql` (SQL enum, если используется)
5. Релевантные `.http` сценарии (`rest-client/scenarios/30-34.http`)
6. `docs/business-process.md` (таблица переходов)
7. `CLAUDE.md` Section 6 (таблица переходов)

### Меняешь / добавляешь event

Обновить:

1. `lib/platform-core/.../events/EventType.java`
2. `lib/platform-core/.../events/TopicNames.java` (если новый topic)
3. `lib/platform-core/.../events/payload/` (payload class)
4. Producer service (`OutboxService.append(...)`)
5. Consumer service (`ProcessedMessageService.handleIfNew(...)`)
6. SQL схемы, если outbox/inbox structure поменялся
7. `docs/event-catalog.md` (producer/consumer matrix)
8. Хотя бы один demo `.http` сценарий

### Меняешь gRPC контракт

Обновить:

1. `lib/grpc-contracts/src/main/proto/*.proto`
2. Service-side implementation (`*GrpcService.java`)
3. Все вызывающие стороны (gateway + другие сервисы через clients)
4. `docs/api-reference.md`

### Меняешь entity-поле

Обновить:

1. Java entity в `domain/`
2. Парный `sql/init_*_service.sql` и `sql/drop_*_service.sql`
3. Связанные query-методы / репозиторий
4. Если поле в payload события — соответствующий payload class

## Что есть в каждой service-БД (общие таблицы)

- `outbox_events` — исходящие события для relay'а
- `processed_messages` — Inbox дедупликации входящих событий
- бизнес-таблицы конкретного сервиса
