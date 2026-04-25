# HTTP API Reference

## 1. Общие замечания

Текущая система пока не имеет внешнего API gateway и полноценной аутентификации. Поэтому:

- actor identity передаётся в request body как `landlordUserId`, `tenantUserId`, `adminUserId`
- `claim-service` синхронно валидирует пользователя через `auth-service`
- это допустимо для текущей учебной стадии, но не является финальной security-моделью

## 2. `auth-service`

Base URL:

- `http://localhost:8082`

### `GET /internal/users/{id}`

Назначение:

- internal lookup для `claim-service`

Response:

- `RemoteUserView`

Поля:

- `id`
- `email`
- `role`
- `enabled`
- `penaltyCount`

### `GET /api/auth/users`

Назначение:

- получить список seeded users для demo и ручной проверки

### `POST /api/auth/users/{id}/deactivate`

Назначение:

- деактивировать пользователя
- запустить saga `USER_DEACTIVATED`

Body:

```json
{
  "reason": "Manual operator deactivation for distributed transaction demo"
}
```

### `GET /actuator/healthz`

Упрощённый health endpoint.

## 3. `claim-service`

Base URL:

- `http://localhost:8081`

### `POST /api/claims`

Назначение:

- создать claim
- перевести его в `ASSESSMENT_IN_PROGRESS`
- записать `CLAIM_CREATED` в outbox

Body:

```json
{
  "landlordUserId": 1,
  "tenantUserId": 3,
  "title": "Broken sofa and stained carpet",
  "description": "Tenant left visible damage and the claim requires asynchronous assessment.",
  "claimedAmount": 250.00,
  "currency": "USD"
}
```

Response:

- `ClaimResponse`

### `GET /api/claims/{id}`

Назначение:

- получить текущее состояние claim

### `GET /api/claims/{id}/timeline`

Назначение:

- получить историю переходов и значимых событий claim

### `POST /api/claims/{id}/additional-info`

Назначение:

- отправить дополнительные материалы после `NEED_ADDITIONAL_INFO`

Body:

```json
{
  "landlordUserId": 1,
  "comment": "Uploaded serial number and room overview separately; reassess please."
}
```

### `POST /api/claims/{id}/tenant-response`

Назначение:

- отправить ответ арендатора

Body:

```json
{
  "tenantUserId": 3,
  "agree": false,
  "comment": "I disagree with the final amount."
}
```

### `POST /api/claims/{id}/support-decision`

Назначение:

- финальное решение администратора

Ветка без штрафа:

```json
{
  "adminUserId": 5,
  "applyPenalty": false,
  "note": "Close without penalty"
}
```

Ветка со штрафом:

```json
{
  "adminUserId": 5,
  "applyPenalty": true,
  "penaltyAmount": 175.00,
  "penaltyCurrency": "USD",
  "note": "Proceed with penalty application",
  "simulateFailure": false
}
```

## 4. `penalty-service`

Base URL:

- `http://localhost:8084`

### `GET /api/penalties/claims/{claimId}`

Назначение:

- получить penalty operations по конкретной заявке

Полезно для:

- диагностики failure path
- поиска `operationId` для retry

### `POST /api/penalties/operations/{operationId}/retry`

Назначение:

- перевести failed operation обратно в `PENDING`

Ограничение:

- retry разрешён только для `FAILED` операций

## 5. `audit-service`

Base URL:

- `http://localhost:8086`

### `GET /api/audit/claims/{claimId}/events`

Назначение:

- получить весь event trail для claim

Используется для:

- демонстрации работы саги
- трассировки `correlationId` и `sagaId`

## 6. `assessment-service` и `notification-service`

Публичного пользовательского REST API в текущей реализации нет.

Они являются внутренними асинхронными участниками системы:

- `assessment-service` — worker
- `notification-service` — read-side consumer

## 7. Ошибки и ограничения API

Общая стратегия ошибок сейчас простая:

- `IllegalArgumentException -> 400`
- `IllegalStateException -> 409`

Это реализовано в `platform-core` через `ApiExceptionHandler`.

## 8. Основные DTO

### `ClaimResponse`

Возвращает:

- `id`
- `correlationId`
- `landlordId`
- `tenantId`
- `status`
- `title`
- `description`
- `claimedAmount`
- `currency`
- `assessmentAmount`
- `assessmentNotes`
- `penaltyAmount`
- `penaltyCurrency`
- `resolutionNote`
- `createdAt`
- `updatedAt`
- `closedAt`

### `RemoteUserView`

Используется во внутреннем sync вызове из `claim-service` в `auth-service`.

Поля:

- `id`
- `email`
- `role`
- `enabled`
- `penaltyCount`
