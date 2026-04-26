# External HTTP API and Internal gRPC Reference

## 1. Общие замечания

Текущая система имеет внешний `api-gateway` и demo JWT-аутентификацию:

- внешний base URL: `http://localhost:8080`
- сначала вызывается `POST /api/auth/login`, затем токен передаётся как `Authorization: Bearer ...`
- gateway принимает HTTP `/api/**` и вызывает backend-сервисы через gRPC
- синхронные межсервисные вызовы внутри системы идут через protobuf-контракты из `lib/grpc-contracts`
- асинхронные бизнес-процессы идут через Kafka + Outbox + Inbox/Saga
- `claim-service` синхронно валидирует пользователя через `AuthRpcService.GetUser`
- поля `landlordUserId`, `tenantUserId`, `adminUserId` оставлены только для прямого service-level debug

## 2. `auth-service`

Base URL:

- external: `http://localhost:8080`
- direct service debug: `http://localhost:8082`

### `POST /api/auth/login`

Назначение:

- получить demo JWT для seeded user

Body:

```json
{
  "userId": 1
}
```

Response:

- `token`
- `user`

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

### Internal gRPC

`AuthRpcService`:

- `Login(LoginRequest) -> LoginResponse`
- `ListUsers(EmptyRequest) -> ListUsersResponse`
- `GetUser(GetUserRequest) -> UserDto`
- `DeactivateUser(DeactivateUserRequest) -> UserDto`

`GetUser` является текущим контрактом синхронной валидации пользователя для `claim-service`.

## 3. `claim-service`

Base URL:

- external: `http://localhost:8080`
- direct service debug: `http://localhost:8081`

### `POST /api/claims`

Назначение:

- создать claim
- перевести его в `ASSESSMENT_IN_PROGRESS`
- записать `CLAIM_CREATED` в outbox

Body:

```json
{
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
  "comment": "Uploaded serial number and room overview separately; reassess please."
}
```

### `POST /api/claims/{id}/tenant-response`

Назначение:

- отправить ответ арендатора

Body:

```json
{
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
  "applyPenalty": false,
  "note": "Close without penalty"
}
```

Ветка со штрафом:

```json
{
  "applyPenalty": true,
  "penaltyAmount": 175.00,
  "penaltyCurrency": "USD",
  "note": "Proceed with penalty application",
  "simulateFailure": false
}
```

### `GET /api/claims/{id}/process-status`

Назначение:

- получить текущий статус long-running claim saga, terminal flag и attachment read model

### `GET /api/claims/{id}/attachments`

Назначение:

- получить claim-side attachment refs и статусы binding

### `POST /api/claims/{id}/repair/reassess`

Назначение:

- вручную перезапустить assessment после failure/manual-review состояния

### `POST /api/claims/{id}/repair/close`

Назначение:

- вручную закрыть зависший claim как `CLOSED_NO_PENALTY`

### Internal gRPC

`ClaimRpcService` покрывает все команды и query внешнего HTTP API:

- `CreateClaim`
- `GetClaim`
- `GetTimeline`
- `GetAttachments`
- `GetProcessStatus`
- `ProvideAdditionalInfo`
- `SubmitTenantResponse`
- `SupportDecision`
- `RepairReassess`
- `RepairClose`

## 4. `penalty-service`

Base URL:

- external: `http://localhost:8080`
- direct service debug: `http://localhost:8084`

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

### Internal gRPC

`PenaltyRpcService`:

- `ListOperationsByClaim`
- `RetryOperation`

## 5. `storage-service`

Base URL:

- external: `http://localhost:8080`
- direct service debug: `http://localhost:8087`

### `POST /api/attachments/init`

Назначение:

- создать attachment metadata и object key для out-of-band MinIO upload
- записать `ATTACHMENT_INITIALIZED` в outbox

Body:

```json
{
  "originalFilename": "sofa-damage.jpg",
  "contentType": "image/jpeg"
}
```

### `POST /api/attachments/{id}/confirm`

Назначение:

- подтвердить, что объект загружен во внешнее хранилище
- записать `ATTACHMENT_CONFIRMED` в outbox

### `GET /api/attachments/{id}`

Назначение:

- получить storage-owned attachment metadata

### Internal gRPC

`StorageRpcService`:

- `InitAttachment`
- `ConfirmAttachment`
- `GetAttachment`
- `ListAttachments`

## 6. `audit-service`

Base URL:

- external: `http://localhost:8080`
- direct service debug: `http://localhost:8086`

### `GET /api/audit/claims/{claimId}/events`

Назначение:

- получить весь event trail для claim

Используется для:

- демонстрации работы саги
- трассировки `correlationId` и `sagaId`

### Internal gRPC

`AuditRpcService`:

- `GetClaimEvents`

## 7. `assessment-service` и `notification-service`

Публичного пользовательского REST API в текущей реализации нет.

Они являются внутренними асинхронными участниками системы:

- `assessment-service` — worker
- `notification-service` — read-side consumer

## 8. Ошибки и ограничения API

Общая стратегия ошибок сейчас простая:

- `IllegalArgumentException -> 400`
- `IllegalStateException -> 409`
- gRPC `INVALID_ARGUMENT -> 400`
- gRPC `FAILED_PRECONDITION -> 409`
- gRPC `UNAVAILABLE/DEADLINE_EXCEEDED -> 502`

Это реализовано в `platform-core` через `ApiExceptionHandler` для прямых debug REST endpoints и в `api-gateway` через `GatewayExceptionHandler` для HTTP-to-gRPC edge flow.

## 9. Основные DTO

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
- `attachments`

### `UserDto`

Используется во внутреннем sync gRPC вызове из `claim-service` в `auth-service`.

Поля:

- `id`
- `email`
- `role`
- `enabled`
- `penaltyCount`
