# Business Process and Claim Lifecycle

## 1. Бизнес-домен

Сервис обрабатывает **заявки на штраф** (claim) между арендодателем (LANDLORD), арендатором (TENANT) и платформой (SUPPORT). Арендодатель жалуется на ущерб от арендатора и просит платформу применить санкцию. Платформа проверяет основания, запрашивает позицию арендатора и принимает решение.

Бизнес-модель этого процесса описана в [bpmn/blps1.bpmn](bpmn/blps1.bpmn) и является **reference model**, а не исполняемой спецификацией. Реальная исполняемая логика — в коде `claim-service` (`ClaimProcessService`, `ClaimStatus`).

## 2. Акторы

| Актор | Роль в системе | Чем занимается |
|---|---|---|
| `LANDLORD` | Заявитель | Создаёт заявку, прикладывает доказательства, отправляет доп. материалы по запросу |
| `TENANT` | Ответчик | Отвечает на претензию (агрее/disagree + комментарий); может проигнорировать |
| `SUPPORT` | Платформа (Airbnb Support) | Принимает финальное решение по claim в `SUPPORT_REVIEW` |
| `ADMIN` | Администратор системы | Деактивирует пользователей, manual recovery penalty failure |
| `SYSTEM` | Async-логика | Assessment по правилам, tenant-response timeout, penalty side-effect |

## 3. Связь BPMN ↔ реализация

| BPMN элемент | Реализация |
|---|---|
| `UserTask_CreateClaim` | `POST /api/claims` → `claim-service.createClaim()` |
| `Activity_1p30r0x` (проверка полноты данных) | внутри `AssessmentWorkflowService` |
| `ExclusiveGateway_EnoughData` | эвристика: `claimedAmount >= 200 && attempt == 1` → требуется доп. инфа |
| `UserTask_ProvideDocs` | `POST /api/claims/{id}/additional-info` |
| `Activity_0gw4i7b` (правила + оценка) | `AssessmentWorkflowService` async worker |
| `ExclusiveGateway_RulesViolated` | эвристика: `claimedAmount >= 50` → есть основания |
| `UserTask_RespondComment` | `POST /api/claims/{id}/tenant-response` |
| `BoundaryTimer_ResponseTimeout` (P3D) | `@Scheduled` job в `claim-service` |
| `UserTask_SupportReview` | manual: `POST /api/claims/{id}/support-decision` |
| `ServiceTask_ApplyPenalty` | async: claim-service → Kafka → penalty-service worker |
| `ServiceTask_CloseWithoutPenalty` | locally в claim-service @Transactional |
| `IntermediateThrowEvent_NotifyDecision` | запись в локальную таблицу `notifications` |

## 4. Жизненный цикл заявки (Claim Status)

Источник истины: `ClaimStatus.java` в `claim-service`.

| Статус | Смысл | Терминальный |
|---|---|---|
| `ASSESSMENT_IN_PROGRESS` | Claim создан, async assessment работает | ❌ |
| `NEED_ADDITIONAL_INFO` | Assessment запросил доп. материалы у landlord | ❌ |
| `AWAITING_TENANT_RESPONSE` | Есть основания, ждём ответ tenant | ❌ |
| `SUPPORT_REVIEW` | Tenant ответил или истёк timeout, ждём решение support | ❌ |
| `PENALTY_PROCESSING` | Support одобрил штраф, penalty-service применяет | ❌ |
| `PENALTY_APPLIED` | Штраф успешно применён | ✅ |
| `PENALTY_PROCESSING_FAILED` | Penalty упал, ждёт manual retry | ❌ (но требует вмешательства) |
| `CLOSED_NO_PENALTY` | Заявка закрыта без штрафа | ✅ |

## 5. Таблица переходов

| Триггер | Откуда | Куда | Кто инициирует |
|---|---|---|---|
| `POST /api/claims` | — | `ASSESSMENT_IN_PROGRESS` | LANDLORD |
| Assessment: `requiresAdditionalInfo=true` | `ASSESSMENT_IN_PROGRESS` | `NEED_ADDITIONAL_INFO` | SYSTEM (assessment worker) |
| Assessment: `penaltyGrounds=true` | `ASSESSMENT_IN_PROGRESS` | `AWAITING_TENANT_RESPONSE` | SYSTEM |
| Assessment: нет оснований | `ASSESSMENT_IN_PROGRESS` | `CLOSED_NO_PENALTY` | SYSTEM |
| `POST /additional-info` | `NEED_ADDITIONAL_INFO` | `ASSESSMENT_IN_PROGRESS` | LANDLORD |
| `POST /tenant-response` | `AWAITING_TENANT_RESPONSE` | `SUPPORT_REVIEW` | TENANT |
| Tenant timeout (P3D) | `AWAITING_TENANT_RESPONSE` | `SUPPORT_REVIEW` | SYSTEM (scheduled) |
| `POST /support-decision applyPenalty=false` | `SUPPORT_REVIEW` | `CLOSED_NO_PENALTY` | SUPPORT |
| `POST /support-decision applyPenalty=true` | `SUPPORT_REVIEW` | `PENALTY_PROCESSING` | SUPPORT |
| `PENALTY_APPLIED` event | `PENALTY_PROCESSING` | `PENALTY_APPLIED` | SYSTEM (via penalty-service) |
| `PENALTY_APPLICATION_FAILED` event | `PENALTY_PROCESSING` | `PENALTY_PROCESSING_FAILED` | SYSTEM |
| Manual retry penalty success | `PENALTY_PROCESSING_FAILED` | `PENALTY_PROCESSING` → `PENALTY_APPLIED` | ADMIN |
| `USER_DEACTIVATED` event | любой нетерминальный | `CLOSED_NO_PENALTY` | SYSTEM (via edge-service) |

## 6. Эвристика assessment (демо-логика)

Реализована в `AssessmentWorkflowService`:

1. Если `attempt_no == 1 && claimedAmount >= 200` → `requiresAdditionalInfo=true`, claim уходит в `NEED_ADDITIONAL_INFO`.
2. Иначе если `claimedAmount >= 50` → `penaltyGrounds=true`, `assessmentAmount = claimedAmount * 0.70`, claim уходит в `AWAITING_TENANT_RESPONSE`.
3. Иначе → `penaltyGrounds=false`, claim уходит в `CLOSED_NO_PENALTY`.

Это **намеренно простая** демо-эвристика, не экспертная система. В прод-варианте здесь был бы внешний rules engine или ML-модель.

## 7. Tenant response (P3D timeout)

Из BPMN: `<bpmn:timeDuration>P3D</bpmn:timeDuration>` — 3 дня на ответ ответчика.

Реализация:

- claim переходит в `AWAITING_TENANT_RESPONSE` при заявленных основаниях для штрафа
- `claim-service` использует Quartz job `TenantResponseTimeoutJob` (cron раз в минуту), который ищет:
  ```sql
  SELECT id FROM claims
  WHERE status = 'AWAITING_TENANT_RESPONSE'
    AND now() - updated_at > interval '3 days'
  FOR UPDATE SKIP LOCKED LIMIT 50
  ```
- для каждой такой claim: @Transactional перевод в `SUPPORT_REVIEW` + timeline + outbox `TENANT_RESPONSE_EXPIRED`

Если tenant успел ответить — claim переходит в `SUPPORT_REVIEW` сразу через REST endpoint, timeout не срабатывает.

## 8. Support decision

Endpoint: `POST /api/claims/{id}/support-decision`

Тело:

```json
{
  "applyPenalty": true,
  "penaltyAmount": 175.00,
  "penaltyCurrency": "USD",
  "note": "Confirmed: damage evidence sufficient",
  "simulateFailure": false
}
```

Ветки:

- `applyPenalty=false` → claim в `CLOSED_NO_PENALTY`, локальный @Transactional + outbox `CLAIM_CLOSED_NO_PENALTY`
- `applyPenalty=true && penaltyAmount > 0` → claim в `PENALTY_PROCESSING` + outbox `PENALTY_APPLICATION_REQUESTED`
- `simulateFailure=true` — флаг для demo, заставляет `penalty-service` имитировать сбой

## 9. Penalty failure path (manual recovery)

Демонстрирует saga с non-rollbackable step:

1. SUPPORT отправляет `support-decision` с `simulateFailure=true`
2. `claim-service` → `PENALTY_PROCESSING` + outbox event
3. `penalty-service` создаёт `penalty_operations(status=PENDING)`
4. Worker → `PROCESSING` → имитирует ошибку → `FAILED`
5. `penalty-service` publishes `PENALTY_APPLICATION_FAILED`
6. `claim-service` → `PENALTY_PROCESSING_FAILED` (intermediate state, требует вмешательства)
7. ADMIN: `POST /api/penalties/operations/{id}/retry`
8. `penalty-service` возвращает operation в `PENDING`, worker обработает заново
9. Успех → `PENALTY_APPLIED` → claim в `PENALTY_APPLIED`

**Компенсация ≠ rollback БД.** Это бизнес-действие: retry или manual close.

## 10. User deactivation как distributed reaction

Один бизнес-факт в `edge-service` запускает реакцию в `claim-service`:

1. ADMIN: `POST /api/auth/users/{id}/deactivate`
2. `edge-service` @Transactional: `users.enabled=false` + outbox `USER_DEACTIVATED`
3. relay → Kafka `identity.events`
4. `claim-service` consumer:
   - dedup через `processed_messages`
   - находит все нетерминальные claims, где user — landlord или tenant
   - переводит их в `CLOSED_NO_PENALTY` с note `"closed due to user deactivation"`
   - пишет timeline + outbox `CLAIM_CLOSED_NO_PENALTY` на каждую

Между состояниями user.enabled=false и claims.status=CLOSED — короткое окно eventual consistency. Это нормально.

## 11. Attachment lifecycle (MinIO saga)

В `claim-service`, но MinIO — non-XA участник:

| Статус attachment | Откуда | Куда | Действие |
|---|---|---|---|
| `INITIALIZED` | — | `INITIALIZED` | `POST /api/attachments/init` создаёт metadata row + presigned PUT URL |
| (вне системы) | `INITIALIZED` | `INITIALIZED` | клиент делает `PUT` напрямую в MinIO |
| `CONFIRMED` | `INITIALIZED` | `CONFIRMED` | `POST /api/attachments/{id}/confirm` делает HEAD-check в MinIO **до** открытия БД-TX, потом @Transactional UPDATE |
| `BOUND` | `CONFIRMED` | `BOUND` | `POST /api/claims` с `attachmentIds[]` — внутри одной @Transactional UPDATE attachments SET claim_id=?, status=BOUND |
| `ORPHANED` | `INITIALIZED`/`CONFIRMED` | `ORPHANED` | reconciliation job убирает attachments > 24h без claim |

## 12. Notification flow (write-side log)

Когда происходит значимое событие lifecycle (transition, успех/ошибка penalty, deactivation, и т.д.):

1. `claim-service` @KafkaListener / @Transactional делает бизнес-update
2. В **той же транзакции** — INSERT в локальную таблицу `notifications`
3. Никакой отдельный сервис не нужен — это локальный read-side log

`GET /api/claims/{id}/notifications` возвращает свежие записи.

Реальная отправка email/push в demo не реализована — это product feature, не архитектурный паттерн.

## 13. Что отличается от BPMN

BPMN — бизнес-референс, реализация имеет осознанные упрощения:

| BPMN | Реализация | Почему |
|---|---|---|
| `Activity_1p30r0x` (проверка полноты) и `Activity_0gw4i7b` (оценка ущерба) — два разных user task | Один `AssessmentWorkflowService` async worker | Это **системные** задачи, не human task. BPMN ошибочно положил их в Lane заявителя. |
| Уведомление как `IntermediateThrowEvent_NotifyDecision` | Локальный `notifications` лог | Реальная отправка вне scope demo |
| `BoundaryTimer_ResponseTimeout = P3D` | `@Scheduled` job каждую минуту, выбирает претеренные | Имитация workflow engine timer |
| Workflow engine исполняет весь BPMN | Не исполняет весь BPMN; Temporal используется точечно для penalty saga | BPMN остается бизнес-референсом, а Temporal хранит состояние распределенной саги, не заменяет всю BPMN-модель |

Не нужно ставить BPMN и код в противоречие. **BPMN — это бизнес-договорённость. Код — её исполняемая интерпретация.**

## 14. Demo-сценарии

| Файл | Демонстрирует |
|---|---|
| `rest-client/scenarios/01-create-claim-async-penalty.http` | Happy path: создание → assessment → tenant response → support → penalty applied |
| `rest-client/scenarios/02-penalty-failure-recovery.http` | Penalty failure → `PENALTY_PROCESSING_FAILED` → manual retry → `PENALTY_APPLIED` |
| `rest-client/scenarios/03-user-deactivation.http` | Деактивация → distributed reaction в claim-service |
| `rest-client/scenarios/04-attachment-saga.http` | Init → MinIO upload → confirm → bind в claim |
| `rest-client/scenarios/05-tenant-timeout.http` | `AWAITING_TENANT_RESPONSE` → P3D timeout → `SUPPORT_REVIEW` |

См. [runbook.md](runbook.md) для команд запуска.
