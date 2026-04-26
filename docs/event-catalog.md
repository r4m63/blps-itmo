# Event Catalog

## 1. Event Envelope

Все события в Kafka публикуются как `EventEnvelope`.

Поля envelope:

| Поле | Тип | Назначение |
| --- | --- | --- |
| `eventId` | `String` | уникальный id события |
| `eventType` | `EventType` | тип доменного события |
| `eventVersion` | `int` | версия схемы события, сейчас `1` |
| `occurredAt` | `Instant` | время создания outbox record |
| `producerService` | `String` | сервис-источник события |
| `correlationId` | `String` | сквозной id процесса |
| `sagaId` | `String` | id конкретной саги |
| `aggregateType` | `String` | тип aggregate, например `CLAIM` или `USER` |
| `aggregateId` | `String` | id aggregate |
| `actorId` | `Long` | пользователь/актер, связанный с действием |
| `payload` | `JsonNode` | конкретная бизнес-нагрузка |

## 2. Список событий

Текущий `EventType`:

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

## 3. Топики

| Topic | Типы событий |
| --- | --- |
| `claim.events` | `CLAIM_CREATED`, `ADDITIONAL_INFO_PROVIDED`, `TENANT_RESPONSE_RECEIVED`, `TENANT_RESPONSE_EXPIRED`, `CLAIM_CLOSED_NO_PENALTY`, `ATTACHMENT_BINDING_REQUESTED` |
| `assessment.events` | `ASSESSMENT_COMPLETED`, `ASSESSMENT_FAILED` |
| `penalty.events` | `PENALTY_APPLICATION_REQUESTED`, `PENALTY_APPLIED`, `PENALTY_APPLICATION_FAILED` |
| `auth.events` | `USER_DEACTIVATED` |
| `storage.events` | `ATTACHMENT_INITIALIZED`, `ATTACHMENT_CONFIRMED`, `ATTACHMENT_BOUND`, `ATTACHMENT_BINDING_FAILED` |

Ключ сообщения в текущей реализации:

- `eventKey = aggregateId`

То есть:

- для claim-based событий ключом становится `claimId`
- для user-based событий ключом становится `userId`

## 4. Producer / Consumer Matrix

| Event | Producer | Consumers |
| --- | --- | --- |
| `CLAIM_CREATED` | `claim-service` | `assessment-service`, `notification-service`, `audit-service` |
| `ADDITIONAL_INFO_PROVIDED` | `claim-service` | `assessment-service`, `notification-service`, `audit-service` |
| `ASSESSMENT_COMPLETED` | `assessment-service` | `claim-service`, `notification-service`, `audit-service` |
| `ASSESSMENT_FAILED` | `assessment-service` | `claim-service`, `notification-service`, `audit-service` |
| `TENANT_RESPONSE_RECEIVED` | `claim-service` | `notification-service`, `audit-service` |
| `TENANT_RESPONSE_EXPIRED` | `claim-service` | `notification-service`, `audit-service` |
| `CLAIM_CLOSED_NO_PENALTY` | `claim-service` | `notification-service`, `audit-service` |
| `PENALTY_APPLICATION_REQUESTED` | `claim-service` | `penalty-service`, `notification-service`, `audit-service` |
| `PENALTY_APPLIED` | `penalty-service` | `claim-service`, `auth-service`, `notification-service`, `audit-service` |
| `PENALTY_APPLICATION_FAILED` | `penalty-service` | `claim-service`, `notification-service`, `audit-service` |
| `USER_DEACTIVATED` | `auth-service` | `claim-service`, `notification-service`, `audit-service` |
| `ATTACHMENT_INITIALIZED` | `storage-service` | `notification-service`, `audit-service` |
| `ATTACHMENT_CONFIRMED` | `storage-service` | `notification-service`, `audit-service` |
| `ATTACHMENT_BINDING_REQUESTED` | `claim-service` | `storage-service`, `notification-service`, `audit-service` |
| `ATTACHMENT_BOUND` | `storage-service` | `claim-service`, `notification-service`, `audit-service` |
| `ATTACHMENT_BINDING_FAILED` | `storage-service` | `claim-service`, `notification-service`, `audit-service` |

## 5. Payload Contracts

### `ClaimCreatedPayload`

| Поле | Тип |
| --- | --- |
| `claimId` | `Long` |
| `landlordId` | `Long` |
| `tenantId` | `Long` |
| `title` | `String` |
| `description` | `String` |
| `claimedAmount` | `BigDecimal` |
| `currency` | `String` |

### `AdditionalInfoProvidedPayload`

| Поле | Тип |
| --- | --- |
| `claimId` | `Long` |
| `landlordId` | `Long` |
| `comment` | `String` |
| `claimedAmount` | `BigDecimal` |
| `currency` | `String` |

### `AssessmentCompletedPayload`

| Поле | Тип |
| --- | --- |
| `claimId` | `Long` |
| `assessmentAmount` | `BigDecimal` |
| `assessmentNotes` | `String` |
| `penaltyGrounds` | `boolean` |
| `requiresAdditionalInfo` | `boolean` |

### `AssessmentFailedPayload`

| Поле | Тип |
| --- | --- |
| `claimId` | `Long` |
| `reason` | `String` |

### `TenantResponsePayload`

| Поле | Тип |
| --- | --- |
| `claimId` | `Long` |
| `tenantId` | `Long` |
| `agree` | `boolean` |
| `comment` | `String` |

### `TenantResponseExpiredPayload`

Текущая реализация публикует expiry как доменный факт claim lifecycle.

| Поле | Тип |
| --- | --- |
| `claimId` | `Long` |
| `reason` | `String` |

### `PenaltyApplicationRequestedPayload`

| Поле | Тип |
| --- | --- |
| `claimId` | `Long` |
| `tenantId` | `Long` |
| `penaltyAmount` | `BigDecimal` |
| `penaltyCurrency` | `String` |
| `note` | `String` |
| `simulateFailure` | `boolean` |

### `PenaltyAppliedPayload`

| Поле | Тип |
| --- | --- |
| `claimId` | `Long` |
| `tenantId` | `Long` |
| `penaltyAmount` | `BigDecimal` |
| `penaltyCurrency` | `String` |
| `operationId` | `Long` |

### `PenaltyApplicationFailedPayload`

| Поле | Тип |
| --- | --- |
| `claimId` | `Long` |
| `tenantId` | `Long` |
| `operationId` | `Long` |
| `reason` | `String` |

### `UserDeactivatedPayload`

| Поле | Тип |
| --- | --- |
| `userId` | `Long` |
| `reason` | `String` |

### Attachment payloads

Attachment saga использует четыре storage-owned события и один claim-owned command event:

- `AttachmentInitializedPayload`
- `AttachmentConfirmedPayload`
- `AttachmentBindingRequestedPayload`
- `AttachmentBoundPayload`
- `AttachmentBindingFailedPayload`

Ключевые поля:

| Поле | Тип |
| --- | --- |
| `attachmentId` | `Long` |
| `attachmentIds` | `List<Long>` |
| `claimId` | `Long` |
| `landlordId` | `Long` |
| `ownerUserId` | `Long` |
| `objectKey` | `String` |
| `originalFilename` | `String` |
| `reason` | `String` |

## 6. `correlationId` и `sagaId`

### `correlationId`

Используется для сквозной связи событий одного бизнес-процесса.

Примеры:

- claim lifecycle после `POST /api/claims`
- цепочка от penalty request до финального penalty result

### `sagaId`

Используется для маркировки конкретной distributed saga.

Текущие шаблоны:

- `claim-lifecycle-{claimId}`
- `penalty-application-{claimId}`
- `user-deactivation-{userId}`
- `attachment-binding-{claimId}`

## 7. Версионирование

Сейчас в `EventEnvelope` поле `eventVersion` всегда равно `1`.

Практический вывод:

- контракт версии фиксирован
- schema evolution пока не внедрена
- при появлении несовместимых изменений нужно будет ввести policy версионирования и совместимости consumer’ов
