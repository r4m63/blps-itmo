# Event Catalog

## 1. EventEnvelope

Все события в Kafka публикуются как универсальный envelope:

```json
{
  "eventId": "uuid",
  "eventType": "CLAIM_CREATED",
  "eventVersion": 1,
  "occurredAt": "2026-05-11T10:23:45.123Z",
  "producerService": "claim-service",
  "correlationId": "uuid",
  "sagaId": "claim-lifecycle-42",
  "aggregateType": "CLAIM",
  "aggregateId": "42",
  "actorId": 1,
  "payload": { ... type-specific JSON ... }
}
```

| Поле | Тип | Назначение |
|---|---|---|
| `eventId` | String (UUID) | Уникальный id события, используется для dedup в Inbox |
| `eventType` | enum `EventType` | Тип доменного события |
| `eventVersion` | int | Версия схемы payload. Сейчас всегда `1` |
| `occurredAt` | Instant | Момент создания outbox row |
| `producerService` | String | `edge-service` / `claim-service` / `penalty-service` |
| `correlationId` | String | Сквозной id бизнес-процесса (передаётся через HTTP `X-Correlation-Id`, gRPC metadata, EventEnvelope) |
| `sagaId` | String | Id конкретной саги (см. шаблоны ниже) |
| `aggregateType` | String | `CLAIM` / `USER` / `PENALTY` / `ATTACHMENT` |
| `aggregateId` | String | id aggregate |
| `actorId` | Long | Пользователь, инициировавший действие |
| `payload` | JsonNode | Бизнес-нагрузка, специфичная для `eventType` |

## 2. Kafka топики

| Topic | Producer | Назначение |
|---|---|---|
| `claim.events` | `claim-service` | Все события lifecycle заявки (включая attachment-стадии) |
| `penalty.events` | `claim-service`, `penalty-service` | Запрос на penalty (от claim) + результат (от penalty) |
| `identity.events` | `edge-service` | События идентичности |
| `claim.events.dlt` | shared error handler | Dead Letter Topic для `claim.events` |
| `penalty.events.dlt` | shared error handler | DLT для `penalty.events` |
| `identity.events.dlt` | shared error handler | DLT для `identity.events` |

**Partition key:** `eventKey = aggregateId`.

- claim chain → `claimId`
- penalty chain → `claimId` (привязка к одному claim, чтобы ordering сохранялся)
- identity chain → `userId`

## 3. EventType enum (полный список)

| EventType | Topic | Payload class | Aggregate |
|---|---|---|---|
| `CLAIM_CREATED` | `claim.events` | `ClaimCreatedPayload` | CLAIM |
| `ADDITIONAL_INFO_PROVIDED` | `claim.events` | `AdditionalInfoProvidedPayload` | CLAIM |
| `ASSESSMENT_COMPLETED` | `claim.events` | `AssessmentCompletedPayload` | CLAIM |
| `ASSESSMENT_FAILED` | `claim.events` | `AssessmentFailedPayload` | CLAIM |
| `TENANT_RESPONSE_RECEIVED` | `claim.events` | `TenantResponseReceivedPayload` | CLAIM |
| `TENANT_RESPONSE_EXPIRED` | `claim.events` | `TenantResponseExpiredPayload` | CLAIM |
| `CLAIM_CLOSED_NO_PENALTY` | `claim.events` | `ClaimClosedNoPenaltyPayload` | CLAIM |
| `ATTACHMENT_INITIALIZED` | `claim.events` | `AttachmentInitializedPayload` | ATTACHMENT |
| `ATTACHMENT_CONFIRMED` | `claim.events` | `AttachmentConfirmedPayload` | ATTACHMENT |
| `ATTACHMENT_BOUND` | `claim.events` | `AttachmentBoundPayload` | ATTACHMENT |
| `PENALTY_APPLICATION_REQUESTED` | `penalty.events` | `PenaltyApplicationRequestedPayload` | CLAIM |
| `PENALTY_APPLIED` | `penalty.events` | `PenaltyAppliedPayload` | CLAIM |
| `PENALTY_APPLICATION_FAILED` | `penalty.events` | `PenaltyApplicationFailedPayload` | CLAIM |
| `USER_DEACTIVATED` | `identity.events` | `UserDeactivatedPayload` | USER |

> **Замечание:** `ASSESSMENT_*` события идут в `claim.events`, а не в отдельный `assessment.events`, потому что assessment больше не отдельный сервис — это in-process worker внутри claim-service. Event Type'ы оставлены для семантики.

## 4. Producer / Consumer Matrix

| Event | Producer | Consumers |
|---|---|---|
| `CLAIM_CREATED` | `claim-service` | `claim-service` (self → assessment worker) |
| `ADDITIONAL_INFO_PROVIDED` | `claim-service` | `claim-service` (self → re-assessment) |
| `ASSESSMENT_COMPLETED` | `claim-service` | `claim-service` (self → status transition) |
| `ASSESSMENT_FAILED` | `claim-service` | `claim-service` (self → timeline entry, claim остаётся в `ASSESSMENT_IN_PROGRESS` до manual `/repair/reassess`) |
| `TENANT_RESPONSE_RECEIVED` | `claim-service` | `claim-service` (self → notifications log) |
| `TENANT_RESPONSE_EXPIRED` | `claim-service` | `claim-service` (self → status SUPPORT_REVIEW) |
| `CLAIM_CLOSED_NO_PENALTY` | `claim-service` | `claim-service` (self → notifications) |
| `ATTACHMENT_INITIALIZED` | `claim-service` | (read-side, audit-only) |
| `ATTACHMENT_CONFIRMED` | `claim-service` | (read-side, audit-only) |
| `ATTACHMENT_BOUND` | `claim-service` | (read-side, audit-only) |
| `PENALTY_APPLICATION_REQUESTED` | `claim-service` | `penalty-service` |
| `PENALTY_APPLIED` | `penalty-service` | `claim-service`, `edge-service` |
| `PENALTY_APPLICATION_FAILED` | `penalty-service` | `claim-service` |
| `USER_DEACTIVATED` | `edge-service` | `claim-service` |

> **Self-consume в claim-service** — намеренное архитектурное решение: даже async обработка assessment проходит через outbox + Kafka + inbox, чтобы продемонстрировать паттерн end-to-end (а не делать прямой in-process method call).

## 5. Payload контракты

Все payload-классы лежат в `lib/platform-core/src/main/java/blps/itmo/platform/events/payload/`. Все поля иммутабельны (`record` или final fields).

### `ClaimCreatedPayload`

| Поле | Тип | Описание |
|---|---|---|
| `claimId` | Long | id новой claim |
| `landlordId` | Long | id арендодателя |
| `tenantId` | Long | id арендатора |
| `title` | String | заголовок |
| `description` | String | текст |
| `claimedAmount` | BigDecimal | заявленная сумма ущерба |
| `currency` | String | ISO-4217 |
| `attachmentIds` | List<Long> | id прикреплённых attachment'ов (могут быть пустыми) |

### `AdditionalInfoProvidedPayload`

| Поле | Тип |
|---|---|
| `claimId` | Long |
| `landlordId` | Long |
| `comment` | String |
| `claimedAmount` | BigDecimal |
| `currency` | String |
| `attemptNo` | int |

### `AssessmentCompletedPayload`

| Поле | Тип | Описание |
|---|---|---|
| `claimId` | Long | |
| `assessmentAmount` | BigDecimal NULL | сумма оценки (null если нет grounds) |
| `assessmentNotes` | String | пояснение |
| `penaltyGrounds` | boolean | есть основания для штрафа |
| `requiresAdditionalInfo` | boolean | требуется ли доп. инфа |

### `AssessmentFailedPayload`

| Поле | Тип |
|---|---|
| `claimId` | Long |
| `reason` | String |

### `TenantResponseReceivedPayload`

| Поле | Тип |
|---|---|
| `claimId` | Long |
| `tenantId` | Long |
| `agree` | boolean |
| `comment` | String |

### `TenantResponseExpiredPayload`

| Поле | Тип |
|---|---|
| `claimId` | Long |
| `reason` | String (например `"P3D timeout exceeded"`) |

### `ClaimClosedNoPenaltyPayload`

| Поле | Тип |
|---|---|
| `claimId` | Long |
| `reason` | String |
| `closedByActor` | Long NULL |

### `PenaltyApplicationRequestedPayload`

| Поле | Тип |
|---|---|
| `claimId` | Long |
| `tenantId` | Long |
| `penaltyAmount` | BigDecimal |
| `penaltyCurrency` | String |
| `note` | String |
| `simulateFailure` | boolean |

### `PenaltyAppliedPayload`

| Поле | Тип |
|---|---|
| `claimId` | Long |
| `tenantId` | Long |
| `penaltyAmount` | BigDecimal |
| `penaltyCurrency` | String |
| `operationId` | Long |

### `PenaltyApplicationFailedPayload`

| Поле | Тип |
|---|---|
| `claimId` | Long |
| `tenantId` | Long |
| `operationId` | Long |
| `reason` | String |

### `UserDeactivatedPayload`

| Поле | Тип |
|---|---|
| `userId` | Long |
| `reason` | String |

### `AttachmentInitializedPayload` / `AttachmentConfirmedPayload` / `AttachmentBoundPayload`

| Поле | Тип |
|---|---|
| `attachmentId` | Long |
| `ownerUserId` | Long |
| `objectKey` | String |
| `originalFilename` | String |
| `claimId` | Long NULL (для `BOUND` обязательно) |

## 6. correlationId и sagaId

### correlationId

Сквозной id одного бизнес-процесса. Генерируется в `edge-service` при первом HTTP-вызове (или принимается из заголовка `X-Correlation-Id`, если клиент его прислал).

Передаётся:

- HTTP → header `X-Correlation-Id`
- gRPC → metadata `x-correlation-id`
- Kafka → поле в `EventEnvelope`
- MDC → во всех structured-логах

### sagaId

Шаблоны:

- `claim-lifecycle-{claimId}` — для self-обработки одной claim (assessment, status transitions)
- `penalty-application-{claimId}` — для penalty саги
- `user-deactivation-{userId}` — для каскадного закрытия claims
- `attachment-binding-{claimId}` — для MinIO saga

`sagaId` создаётся **producer'ом** инициирующего события и пробрасывается всеми участниками саги.

## 7. Версионирование

Сейчас `eventVersion = 1` для всех событий.

### Когда придётся вводить эволюцию

- Добавление **опционального** поля в payload — совместимо backward, version не меняется. Consumer'ы должны игнорировать неизвестные поля (Jackson default).
- **Удаление** поля или **переименование** — несовместимо. Нужно:
  1. Завести новый payload class (например `ClaimCreatedPayloadV2`)
  2. Поднять `eventVersion=2` для нового producer'а
  3. Consumer должен уметь обрабатывать оба `eventVersion` параллельно в течение transition периода

### Что НЕ реализовано (явно)

- Schema registry (Confluent, Apicurio) — payload'ы валидируются only через Java типы при десериализации
- Backward/forward compatibility checks в CI — добавляются позже

## 8. Семантика at-least-once

Outbox + Kafka publish — **at-least-once**. Это значит:

- одно и то же событие может быть доставлено consumer'у **несколько раз**
- caller обязан быть идемпотентным (см. processed_messages)

**At-most-once** для side-effect достигается inbox-паттерном на стороне consumer'а — это контракт, который должен соблюдать каждый `@KafkaListener`.

## 9. Запрещено

- ❌ Публиковать в Kafka напрямую через `kafkaTemplate.send(...)`. Только через outbox.
- ❌ Делать payload mutable / разделять между нитями.
- ❌ Включать в payload **owned data чужого сервиса** (например в `PenaltyAppliedPayload` НЕ кладём `tenant.email` — penalty-service это не владеет).
- ❌ Менять схему payload без поднятия `eventVersion` если изменение несовместимо.
- ❌ Использовать random/timestamp как `eventKey` — теряется ordering.
