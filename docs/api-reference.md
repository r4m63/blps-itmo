# API Reference

## 1. Общие замечания

- Внешний base URL: **`http://localhost:8080`** (`edge-service`)
- Внутри системы все sync-вызовы между сервисами идут через **gRPC** (контракты в `lib/grpc-contracts/src/main/proto/`)
- Между сервисами **нет** HTTP-вызовов
- Async бизнес-процессы — через Kafka + Outbox + Inbox / Saga
- Authentication: demo JWT через `POST /api/auth/login`, далее `Authorization: Bearer <token>`
- Все запросы могут содержать заголовок `X-Correlation-Id` (если не прислан — генерируется edge-service)

## 2. Стандартный response для async операций

Long-running операции возвращают **HTTP 202 Accepted**:

```json
{
  "claimId": 42,
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "processingStatus": "ASSESSMENT_IN_PROGRESS",
  "checkStatusAt": "/api/claims/42/process-status"
}
```

Sync операции возвращают **200 OK** или **201 Created** с конечным состоянием ресурса.

## 3. Стандартные ошибки

| HTTP | Условие | Тип |
|---|---|---|
| 400 | Невалидный body / параметры | `IllegalArgumentException` / `MethodArgumentNotValidException` |
| 401 | JWT отсутствует / истёк | `AuthenticationException` |
| 403 | Нет прав на действие (например LANDLORD пытается сделать support-decision) | `AccessDeniedException` |
| 404 | Ресурс не найден | `EntityNotFoundException` |
| 409 | Конфликт состояния (например support-decision на claim в `PENALTY_APPLIED`) | `IllegalStateException` |
| 422 | Невалидный переход state machine | `IllegalStateException` |
| 502 | gRPC backend недоступен | gRPC `UNAVAILABLE` / `DEADLINE_EXCEEDED` |
| 503 | Circuit breaker открыт | Resilience4j |

Тело ошибки:

```json
{
  "timestamp": "2026-05-11T10:23:45.123Z",
  "status": 409,
  "error": "Conflict",
  "message": "Cannot apply support decision: claim is in PENALTY_APPLIED",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "path": "/api/claims/42/support-decision"
}
```

---

## 4. `edge-service` (auth + identity + edge)

Direct service port for local debug: `8082` (HTTP), `19082` (gRPC).

### `POST /api/auth/login`

Получить demo JWT.

Request:

```json
{
  "userId": 1
}
```

Response 200:

```json
{
  "token": "eyJ...",
  "expiresIn": 3600,
  "user": {
    "id": 1,
    "email": "landlord1@example.com",
    "role": "LANDLORD",
    "enabled": true,
    "penaltyCount": 0
  }
}
```

### `GET /api/auth/users`

Список seeded users для demo и ручной проверки.

Response 200:

```json
[
  { "id": 1, "email": "landlord1@example.com", "role": "LANDLORD", "enabled": true, "penaltyCount": 0 },
  ...
]
```

### `GET /api/auth/users/{id}`

Получить пользователя по id. Требует JWT.

### `POST /api/auth/users/{id}/deactivate`

Деактивировать пользователя. Запускает saga `user-deactivation-{userId}`.

Требует роль `ADMIN`.

Request:

```json
{ "reason": "Manual deactivation by admin" }
```

Response 202:

```json
{
  "userId": 5,
  "correlationId": "...",
  "processingStatus": "DEACTIVATION_PROPAGATING"
}
```

### `GET /actuator/health`

Стандартный Spring Boot Actuator с custom indicator'ами (outbox lag, Kafka connection, БД).

### Internal gRPC: `IdentityRpcService`

```protobuf
service IdentityRpcService {
  rpc Login(LoginRequest) returns (LoginResponse);
  rpc ListUsers(Empty) returns (ListUsersResponse);
  rpc GetUser(GetUserRequest) returns (UserDto);
  rpc DeactivateUser(DeactivateUserRequest) returns (UserDto);
}
```

`GetUser` — основной sync-контракт валидации actor'а из `claim-service`. Возвращает `UserDto { id, email, role, enabled, penaltyCount }`.

---

## 5. `claim-service`

Direct service port for local debug: `8081` (HTTP), `19081` (gRPC).

Все вызовы через `edge-service` на `http://localhost:8080`.

### `POST /api/claims`

Создать claim. Запускает saga `claim-lifecycle-{claimId}`.

Требует роль `LANDLORD`.

Request:

```json
{
  "tenantUserId": 3,
  "title": "Broken sofa and stained carpet",
  "description": "Tenant left visible damage; assessment required.",
  "claimedAmount": 250.00,
  "currency": "USD",
  "attachmentIds": [101, 102]
}
```

Response 202:

```json
{
  "claimId": 42,
  "correlationId": "...",
  "processingStatus": "ASSESSMENT_IN_PROGRESS",
  "checkStatusAt": "/api/claims/42/process-status"
}
```

### `GET /api/claims/{id}`

Получить текущее состояние claim.

Response 200:

```json
{
  "id": 42,
  "correlationId": "...",
  "landlordId": 1,
  "tenantId": 3,
  "status": "PENALTY_APPLIED",
  "title": "...",
  "description": "...",
  "claimedAmount": 250.00,
  "currency": "USD",
  "assessmentAmount": 175.00,
  "assessmentNotes": "Penalty grounds confirmed",
  "penaltyAmount": 175.00,
  "penaltyCurrency": "USD",
  "resolutionNote": "Applied via operation #58",
  "createdAt": "2026-05-11T10:00:00Z",
  "updatedAt": "2026-05-11T10:15:32Z",
  "closedAt": "2026-05-11T10:15:32Z",
  "attachments": [
    { "id": 101, "objectKey": "uuid-1", "originalFilename": "damage1.jpg", "status": "BOUND" }
  ]
}
```

### `GET /api/claims/{id}/process-status`

Лёгкий endpoint для polling клиентом.

Response 200:

```json
{
  "claimId": 42,
  "status": "ASSESSMENT_IN_PROGRESS",
  "terminal": false,
  "correlationId": "...",
  "updatedAt": "2026-05-11T10:00:05Z"
}
```

### `GET /api/claims/{id}/timeline`

История переходов и значимых событий.

Response 200:

```json
[
  { "eventType": "CLAIM_CREATED", "fromStatus": null, "toStatus": "ASSESSMENT_IN_PROGRESS", "actorId": 1, "note": null, "createdAt": "..." },
  { "eventType": "ASSESSMENT_COMPLETED", "fromStatus": "ASSESSMENT_IN_PROGRESS", "toStatus": "AWAITING_TENANT_RESPONSE", "actorId": null, "note": "Grounds confirmed", "createdAt": "..." },
  ...
]
```

### `GET /api/claims/{id}/notifications`

Локальный лог уведомлений (read-side проекция).

### `GET /api/claims/{id}/attachments`

Attachment refs claim'а.

### `POST /api/claims/{id}/additional-info`

Landlord досылает материалы после `NEED_ADDITIONAL_INFO`.

Request:

```json
{
  "comment": "Uploaded room overview separately; please reassess.",
  "attachmentIds": [103]
}
```

Response 202.

### `POST /api/claims/{id}/tenant-response`

Tenant отвечает на претензию.

Request:

```json
{
  "agree": false,
  "comment": "I disagree with the amount."
}
```

Response 200 (sync transition `AWAITING_TENANT_RESPONSE` → `SUPPORT_REVIEW`).

### `POST /api/claims/{id}/support-decision`

Финальное решение SUPPORT.

Без штрафа:

```json
{
  "applyPenalty": false,
  "note": "Insufficient evidence"
}
```

Со штрафом:

```json
{
  "applyPenalty": true,
  "penaltyAmount": 175.00,
  "penaltyCurrency": "USD",
  "note": "Confirmed",
  "simulateFailure": false
}
```

Response 202 (async — penalty applied через saga).

### `POST /api/claims/{id}/repair/reassess`

Manual recovery: сбросить в `ASSESSMENT_IN_PROGRESS` и запустить новый assessment job.

Требует роль `ADMIN`.

### `POST /api/claims/{id}/repair/close`

Manual recovery: принудительно закрыть как `CLOSED_NO_PENALTY`.

Требует роль `ADMIN`.

Request:

```json
{ "reason": "Manual close: claim stuck for >7 days" }
```

### Attachments

#### `POST /api/attachments/init`

Создать attachment metadata + presigned MinIO PUT URL.

Request:

```json
{
  "originalFilename": "sofa-damage.jpg",
  "contentType": "image/jpeg"
}
```

Response 201:

```json
{
  "attachmentId": 101,
  "objectKey": "uuid-1",
  "uploadUrl": "https://minio/...?signature=...",
  "expiresIn": 3600
}
```

#### `POST /api/attachments/{id}/confirm`

Подтвердить, что объект залит в MinIO (claim-service делает HEAD-check **до** транзакции).

Response 200:

```json
{ "attachmentId": 101, "status": "CONFIRMED" }
```

#### `GET /api/attachments/{id}`

Метаданные attachment.

### Internal gRPC: `ClaimRpcService`

```protobuf
service ClaimRpcService {
  rpc CreateClaim(CreateClaimRequest) returns (ClaimResponse);
  rpc GetClaim(GetClaimRequest) returns (ClaimResponse);
  rpc GetProcessStatus(GetProcessStatusRequest) returns (ProcessStatusResponse);
  rpc GetTimeline(GetTimelineRequest) returns (TimelineResponse);
  rpc GetAttachments(GetAttachmentsRequest) returns (AttachmentsResponse);
  rpc ProvideAdditionalInfo(AdditionalInfoRequest) returns (ClaimResponse);
  rpc SubmitTenantResponse(TenantResponseRequest) returns (ClaimResponse);
  rpc SupportDecision(SupportDecisionRequest) returns (ClaimResponse);
  rpc RepairReassess(RepairRequest) returns (ClaimResponse);
  rpc RepairClose(RepairRequest) returns (ClaimResponse);
  rpc InitAttachment(InitAttachmentRequest) returns (AttachmentResponse);
  rpc ConfirmAttachment(ConfirmAttachmentRequest) returns (AttachmentResponse);
  rpc GetAttachment(GetAttachmentRequest) returns (AttachmentResponse);
}
```

---

## 6. `penalty-service`

Direct service port for local debug: `8084` (HTTP), `19084` (gRPC).

### `GET /api/penalties/claims/{claimId}`

Penalty operations по claim'у. Полезно для диагностики failure path и поиска `operationId` для retry.

Response 200:

```json
[
  {
    "operationId": 58,
    "claimId": 42,
    "tenantId": 3,
    "penaltyAmount": 175.00,
    "penaltyCurrency": "USD",
    "status": "APPLIED",
    "createdAt": "...",
    "processedAt": "...",
    "reason": null
  }
]
```

### `POST /api/penalties/operations/{operationId}/retry`

Перевести FAILED operation в PENDING. Worker подберёт.

Требует роль `ADMIN`.

Ограничение: retry разрешён **только** для `status=FAILED` operations.

Response 200:

```json
{ "operationId": 58, "status": "PENDING" }
```

### Internal gRPC: `PenaltyRpcService`

```protobuf
service PenaltyRpcService {
  rpc ListOperationsByClaim(ListOperationsRequest) returns (OperationsResponse);
  rpc RetryOperation(RetryOperationRequest) returns (OperationResponse);
}
```

---

## 7. Заголовки

| Header | Откуда | Куда | Назначение |
|---|---|---|---|
| `Authorization: Bearer <jwt>` | client | edge-service | Аутентификация |
| `X-Correlation-Id` | client → edge → backend | пробрасывается во все слои + Kafka envelope + MDC | Трассировка |
| `X-User-Id` | edge-service → backend (gRPC metadata) | claim-service / penalty-service | Идентификация actor'а после JWT validation на edge |
| `X-User-Role` | edge-service → backend | claim-service / penalty-service | Авторизация |
| `Idempotency-Key` | client → edge-service | (не реализован в v1) | Защита от дубликатов POST |

## 8. gRPC error mapping

В `platform-core` есть `ApiExceptionHandler`, который мапит gRPC ошибки на HTTP:

| Java exception | gRPC `Status` | HTTP |
|---|---|---|
| `IllegalArgumentException` | `INVALID_ARGUMENT` | 400 |
| `EntityNotFoundException` | `NOT_FOUND` | 404 |
| `IllegalStateException` | `FAILED_PRECONDITION` | 409 |
| `AccessDeniedException` | `PERMISSION_DENIED` | 403 |
| `AuthenticationException` | `UNAUTHENTICATED` | 401 |
| `TimeoutException` | `DEADLINE_EXCEEDED` | 504 |
| backend down | `UNAVAILABLE` | 502 |

## 9. Размер payload'ов и rate limiting

В demo не реализованы:

- max request body size — defaults Spring Boot (10 MB)
- rate limiting — нет (для prod нужен Resilience4j RateLimiter или Bucket4j на edge)
- request timeout — gRPC client default 5s, override через `grpc.client.deadline`
