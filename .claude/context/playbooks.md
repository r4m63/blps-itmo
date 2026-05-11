# Task Playbooks

Короткие playbook'и для частых типов задач. Каждый указывает на конкретные файлы, которые нужно тронуть, и чеклист изменений. Источник истины — код, эти playbook'и догоняют код.

---

## Playbook: Architecture Sweep

Использовать когда задача касается нескольких сервисов или вопрос "как сейчас работает X".

**Читать по порядку:**

1. `settings.gradle.kts`, `build.gradle.kts`
2. `docker-compose.yml`, `.env.sample`
3. `docs/lab3-runbook.md`
4. `CLAUDE.md` Section 1-7
5. `lib/platform-core/` — общие event/outbox/inbox/kafka building blocks
6. `Application` / `Controller` / `Service` файлы релевантного сервиса
7. Парный `sql/init_*_service.sql`

**Что определить на выходе:**

- какой сервис владеет данными
- внешний HTTP / внутренний sync gRPC / async Kafka — что используется
- какое событие стартует и какое завершает flow
- какая DB схема должна оставаться синхронизированной

---

## Playbook: Claim Lifecycle Change

Когда меняются статусы, валидация claim, timeline, orchestration логика.

**Файлы:**

- `services/claim-service/.../service/ClaimProcessService.java`
- `services/claim-service/.../controller/ClaimController.java`
- `services/claim-service/.../domain/ClaimStatus.java`
- `services/claim-service/.../service/ClaimEventListeners.java`
- `sql/init_claim_service.sql`
- `rest-client/scenarios/30-lab3-async-penalty.http`
- `rest-client/scenarios/31-lab3-penalty-failure-recovery.http`

**Чеклист:**

- [ ] подтвердить allowed source и target статус
- [ ] обновить emit-ируемое событие при изменении lifecycle
- [ ] обновить запись в `claim_timeline`
- [ ] синхронизировать landlord/tenant/admin валидацию с `auth-service`
- [ ] SQL enum = Java enum
- [ ] обновить `.http` сценарии если поменялось внешнее поведение

---

## Playbook: Event Contract / Distributed Transaction Change

Когда добавляешь новое событие, меняешь producer/consumer behavior, или трогаешь outbox/inbox.

**Файлы:**

- `lib/platform-core/.../events/EventType.java`
- `lib/platform-core/.../events/TopicNames.java`
- `lib/platform-core/.../events/payload/`
- `lib/platform-core/.../outbox/OutboxService.java`
- `lib/platform-core/.../outbox/ProcessedMessageService.java`
- `lib/platform-core/.../outbox/OutboxRelay.java`
- `lib/platform-core/.../kafka/PlatformKafkaConfig.java`

**Чеклист:**

- [ ] business write + outbox write в одной локальной транзакции
- [ ] consumer идемпотентен через `processed_messages`
- [ ] никаких прямых DB-чтений между сервисами
- [ ] обновлены все producer и consumer, на которые влияет событие
- [ ] SQL схемы синхронизированы если outbox/inbox structure поменялся
- [ ] хотя бы один demo `.http` сценарий доказывает использование события
- [ ] `docs/event-catalog.md` обновлён

---

## Playbook: Gateway / Security Change

Когда меняешь внешний routing, login, JWT claims, propagation actor-заголовков.

**Файлы:**

- `services/api-gateway/.../GatewayAuthFilter.java`
- `services/api-gateway/.../GatewayHttpController.java`
- `services/api-gateway/.../GatewayGrpcClients.java`
- `services/api-gateway/.../GatewayExceptionHandler.java`
- `lib/grpc-contracts/src/main/proto/`
- `lib/platform-core/.../security/DemoJwtService.java`
- `services/auth-service/.../controller/AuthController.java`
- `services/auth-service/.../service/AuthUserService.java`

**Чеклист:**

- [ ] `/api/auth/login` остаётся public
- [ ] `/internal/**` не светится через gateway routing
- [ ] propagation `X-User-Id`, `X-User-Role`, `X-Correlation-Id`
- [ ] gateway остаётся HTTP edge и зовёт backend через gRPC stub'ы
- [ ] обновить `.http` сценарии если поменялась форма внешнего запроса

---

## Playbook: Storage / Attachment Saga Change

Когда меняешь attachment init/confirm/bind или ownership MinIO-метаданных.

**Файлы:**

- `services/storage-service/.../controller/AttachmentController.java`
- `services/storage-service/.../grpc/StorageGrpcService.java`
- `services/storage-service/.../service/StorageWorkflowService.java`
- `services/storage-service/.../domain/Attachment.java`
- `services/claim-service/.../service/ClaimProcessService.java`
- `sql/init_storage_service.sql`
- `rest-client/scenarios/33-lab3-attachment-saga.http`

**Чеклист:**

- [ ] MinIO object metadata владеет `storage-service`
- [ ] на стороне claim — только refs / read-model
- [ ] последовательность `ATTACHMENT_BINDING_REQUESTED → ATTACHMENT_BOUND/FAILED`
- [ ] идемпотентность через `processed_messages`

---

## Playbook: Assessment Rule Change

Когда меняется эвристика оценки или поведение async job.

**Файлы:**

- `services/assessment-service/.../service/AssessmentWorkflowService.java`
- `services/assessment-service/.../domain/AssessmentJob.java`
- `services/assessment-service/.../service/ClaimEventListener.java`
- `sql/init_assessment_service.sql`
- `rest-client/scenarios/30-lab3-async-penalty.http`

**Чеклист:**

- [ ] event ingestion отделён от async processing
- [ ] first-attempt и re-assessment поведение явное
- [ ] идемпотентность на duplicate `CLAIM_CREATED` / `ADDITIONAL_INFO_PROVIDED`
- [ ] проверить итоговые transition'ы в `claim-service`

---

## Playbook: Penalty Flow Change

Когда меняешь применение penalty, failure handling, retry.

**Файлы:**

- `services/penalty-service/.../service/PenaltyWorkflowService.java`
- `services/penalty-service/.../service/PenaltyController.java`
- `services/penalty-service/.../domain/PenaltyOperation.java`
- `services/claim-service/.../service/ClaimProcessService.java`
- `services/auth-service/.../service/AuthUserService.java`
- `sql/init_penalty_service.sql`
- `rest-client/scenarios/31-lab3-penalty-failure-recovery.http`

**Чеклист:**

- [ ] `PENALTY_PROCESSING` остаётся explicit intermediate state
- [ ] success/failure события эмиттит `penalty-service`, не `claim-service`
- [ ] retry flow идемпотентен
- [ ] `auth-service` корректно реагирует на `PENALTY_APPLIED`

---

## Playbook: Auth / User Deactivation Change

Когда меняешь demo users, roles, internal user lookup, поведение деактивации.

**Файлы:**

- `services/auth-service/.../controller/AuthController.java`
- `services/auth-service/.../grpc/AuthGrpcService.java`
- `lib/grpc-contracts/src/main/proto/auth.proto`
- `services/auth-service/.../service/AuthUserService.java`
- `services/auth-service/.../domain/User.java`, `UserRole.java`
- `sql/init_auth_service.sql`
- `rest-client/scenarios/32-lab3-user-deactivation.http`
- `rest-client/scenarios/33-lab3-attachment-saga.http`

**Чеклист:**

- [ ] если меняется user schema — обновить seed-логику и SQL
- [ ] `AuthRpcService.GetUser` стабилен, пока не обновлены ВСЕ gRPC caller'ы
- [ ] деактивация продолжает эмиттить `USER_DEACTIVATED`
- [ ] cleanup claim'ов на деактивацию — в `claim-service`, не в `auth-service`

---

## Playbook: Read-Side Consumer Change

Когда меняешь `notification-service` или `audit-service`.

**Файлы:**

- `services/notification-service/.../service/NotificationEventListener.java`
- `services/audit-service/.../service/AuditEventListener.java`
- `services/audit-service/.../controller/AuditController.java`
- `sql/init_notification_service.sql`
- `sql/init_audit_service.sql`

**Чеклист:**

- [ ] consumer'ы идемпотентны
- [ ] read-side НЕ становится source-of-truth владельцем
- [ ] сохранены `correlationId`, `aggregateType`, `aggregateId` в trail

---

## Playbook: SQL Schema Sync

Когда поменялись entity fields или enums.

**Файлы:**

- соответствующий `domain/` entity
- `sql/init_*_service.sql` + `sql/drop_*_service.sql`

**Чеклист:**

- [ ] один сервис — одна пара schema-файлов
- [ ] enum values одинаковы между Java и SQL
- [ ] индексы по текущим query path сохранены
- [ ] `outbox_events` и `processed_messages` присутствуют в каждой service-БД

---

## Playbook: Scenario-Driven Verification

Когда пользователь спрашивает что реализовано / просит проверить бизнес-поведение.

**Файлы:**

- `rest-client/scenarios/README.md`
- `rest-client/scenarios/30-lab3-async-penalty.http`
- `rest-client/scenarios/31-lab3-penalty-failure-recovery.http`
- `rest-client/scenarios/32-lab3-user-deactivation.http`
- `rest-client/scenarios/33-lab3-attachment-saga.http`
- `rest-client/scenarios/34-lab3-penalty-threshold-cascade.http`

**Что извлечь:**

- какой сервис получает initial request
- какое событие(я) ведут flow дальше
- где видна eventual consistency
- как демонстрируется failure / retry

---

## Playbook: Manual Verification (smoke-test)

Когда нет автотеста на изменение.

**Быстрый путь:**

```bash
docker compose up -d
./gradlew :<module>:bootRun                          # запустить нужные сервисы
# выполнить ближайший сценарий из rest-client/scenarios/
./gradlew build                                       # перед закрытием существенных изменений
```

---

## Playbook: Review Mode

Когда пользователь просит ревью.

**Приоритеты:**

1. Broken claim state transitions
2. Mismatched event producer/consumer contracts
3. Lost-idempotency risks (consumer без `processed_messages`)
4. Schema drift между entity и SQL
5. Stale docs / scenarios, описывающие компоненты которые больше не существуют
6. Прямой `kafkaTemplate.send` в обход Outbox
7. gRPC / HTTP / Kafka send внутри `@Transactional`
