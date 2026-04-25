# Бизнес-процесс и Claim Lifecycle

## 1. Роль BPMN

Файл [bpmn/blps1.bpmn](../bpmn/blps1.bpmn) остаётся бизнес-референсом процесса, но не исполняется workflow engine’ом.

Реальная исполняемая логика находится в:

- `claim-service`
- `assessment-service`
- `penalty-service`
- `auth-service`

То есть BPMN в проекте — это **reference model**, а не runtime engine definition.

## 2. Акторы процесса

- `LANDLORD` — создаёт заявку и при необходимости отправляет дополнительные материалы
- `TENANT` — отвечает на претензию, если дошло до этой стадии
- `ADMIN` — принимает финальное решение по claim
- `SYSTEM` — асинхронно выполняет assessment и penalty application

## 3. Жизненный цикл заявки

Источник истины:

- `ClaimStatus.java`
- `ClaimProcessService`
- `sql/init_claim_service.sql`

Текущие статусы:

- `ASSESSMENT_IN_PROGRESS`
- `NEED_ADDITIONAL_INFO`
- `AWAITING_TENANT_RESPONSE`
- `SUPPORT_REVIEW`
- `PENALTY_PROCESSING`
- `PENALTY_APPLIED`
- `PENALTY_PROCESSING_FAILED`
- `CLOSED_NO_PENALTY`

## 4. Таблица переходов

| Действие | Откуда | Куда | Кто инициирует |
| --- | --- | --- | --- |
| `POST /api/claims` | нет | `ASSESSMENT_IN_PROGRESS` | `LANDLORD` |
| `ASSESSMENT_COMPLETED` с `requiresAdditionalInfo=true` | `ASSESSMENT_IN_PROGRESS` | `NEED_ADDITIONAL_INFO` | `assessment-service` |
| `ASSESSMENT_COMPLETED` с `penaltyGrounds=true` | `ASSESSMENT_IN_PROGRESS` | `AWAITING_TENANT_RESPONSE` | `assessment-service` |
| `ASSESSMENT_COMPLETED` без grounds | `ASSESSMENT_IN_PROGRESS` | `CLOSED_NO_PENALTY` | `assessment-service` |
| `POST /additional-info` | `NEED_ADDITIONAL_INFO` | `ASSESSMENT_IN_PROGRESS` | `LANDLORD` |
| `POST /tenant-response` | `AWAITING_TENANT_RESPONSE` | `SUPPORT_REVIEW` | `TENANT` |
| `POST /support-decision` без штрафа | `SUPPORT_REVIEW` | `CLOSED_NO_PENALTY` | `ADMIN` |
| `POST /support-decision` со штрафом | `SUPPORT_REVIEW` | `PENALTY_PROCESSING` | `ADMIN` |
| `PENALTY_APPLIED` | `PENALTY_PROCESSING` | `PENALTY_APPLIED` | `penalty-service` |
| `PENALTY_APPLICATION_FAILED` | `PENALTY_PROCESSING` | `PENALTY_PROCESSING_FAILED` | `penalty-service` |
| `USER_DEACTIVATED` | любой незакрытый статус | `CLOSED_NO_PENALTY` | `auth-service` |

## 5. Бизнес-эвристика оценки

В текущей реализации assessment является не экспертной системой, а демонстрационным async worker’ом.

Текущие правила:

- если это первая попытка и `claimedAmount >= 200`, то система требует дополнительные материалы
- если `claimedAmount >= 50` и дополнительных материалов больше не требуется, то есть основания для штрафа
- если оснований нет, claim закрывается без штрафа
- при наличии оснований `assessmentAmount = claimedAmount * 0.70`

Это зашито в `AssessmentWorkflowService`.

## 6. Роль tenant response

Если claim дошёл до `AWAITING_TENANT_RESPONSE`, арендатор отправляет ответ:

- endpoint: `POST /api/claims/{id}/tenant-response`
- результат: claim переводится в `SUPPORT_REVIEW`

Замечание:

- поле `agree` в текущей реализации сохраняется в event payload, но не меняет state machine напрямую

## 7. Роль support decision

Endpoint:

- `POST /api/claims/{id}/support-decision`

Ветки:

- `applyPenalty=false`:
  claim закрывается сразу как `CLOSED_NO_PENALTY`
- `applyPenalty=true`:
  claim уходит в `PENALTY_PROCESSING`, а дальше судьба заявки зависит от `penalty-service`

## 8. Failure path

Penalty flow умеет демонстрировать частичный сбой:

- администратор отправляет `simulateFailure=true`
- `penalty-service` создаёт failed operation
- claim уходит в `PENALTY_PROCESSING_FAILED`
- оператор повторяет операцию через `/api/penalties/operations/{id}/retry`
- после повторной обработки claim доходит до `PENALTY_APPLIED`

## 9. User deactivation как бизнес-событие

User deactivation — это не просто изменение строки в `users`.

Фактический бизнес-эффект:

1. `auth-service` деактивирует пользователя
2. публикует `USER_DEACTIVATED`
3. `claim-service` находит все открытые claim этого пользователя
4. закрывает их как `CLOSED_NO_PENALTY`
5. записывает timeline

Это отдельная кросс-контекстная saga.

## 10. Mapping на текущие demo-сценарии

### `30-lab3-async-penalty.http`

Покрывает:

- create claim
- initial async assessment
- additional info
- reassessment
- tenant response
- support decision
- penalty application success

### `31-lab3-penalty-failure-recovery.http`

Покрывает:

- create claim
- async assessment без additional info
- tenant response
- penalty failure
- manual retry
- final success

### `32-lab3-user-deactivation.http`

Покрывает:

- create active claim
- user deactivation
- asynchronous closure of claims

## 11. Отличия от "идеального" BPMN

Сейчас в проекте отсутствуют:

- timeout path для tenant response
- dedicated notification task в основном бизнес-процессе
- отдельный orchestration engine
- attachment/storage subprocess

Это важно: документация проекта должна различать **бизнес-модель** и **текущую реализованную исполняемую модель**.
