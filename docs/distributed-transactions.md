# Distributed Transactions Design

## 1. Проблема, которую решает проект

Проект показывает, как строить межсервисный бизнес-процесс без глобального transaction manager.

Основные риски распределённых транзакций здесь такие:

1. Бизнес-данные записались, а событие в Kafka не ушло.
2. Событие дошло до consumer’а несколько раз.
3. Один сервис уже закоммитил локальное состояние, а другой ещё нет.
4. В середине саги произошёл частичный отказ.

## 2. Почему здесь нет XA

В микросервисной версии проекта глобальный `XA/2PC` намеренно не используется.

Причины:

- микросервисы владеют разными БД
- Kafka не должна участвовать в распределённом commit с бизнес-БД
- глобальный coordinator ухудшает масштабирование и отказоустойчивость
- внешние side effects вроде penalty processing плохо ложатся в модель rollback

Итоговая стратегия:

- локальная транзакция на сервис
- gRPC для синхронной валидации и query/command вызовов без общей транзакции
- outbox для публикации
- inbox для дедупликации
- choreography saga

## 3. Текущие паттерны

## 3.1. Local Transaction

Каждый write use case меняет только свою локальную БД.

Примеры:

- `claim-service.createClaim()`
- `assessment-service.handleClaimCreated()`
- `penalty-service.handlePenaltyRequested()`
- `auth-service.deactivateUser()`

## 3.2. Transactional Outbox

Событие не публикуется напрямую из бизнес-кода как единственный источник истины.

Правильная последовательность:

1. изменить business state
2. вставить запись в `outbox_events`
3. закоммитить транзакцию
4. `OutboxRelay` публикует событие в Kafka

Что это даёт:

- если Kafka временно недоступна, бизнес-операция не теряется
- событие остаётся в БД как `NEW` или `FAILED`
- relay попробует отправить его снова

## 3.3. Inbox / `processed_messages`

Каждый consumer обязан быть идемпотентным.

Правильная последовательность:

1. прочитать событие
2. проверить `(event_id, consumer_name)` в `processed_messages`
3. если уже обработано, выйти без side effect
4. если нет, выполнить локальное изменение
5. записать `processed_messages`

Это защищает от:

- повторной доставки
- повторного poll/consume
- повторного запуска consumer после сбоя

## 3.4. Saga Choreography

Сага в проекте не централизована отдельным orchestrator’ом.

Модель:

- один сервис публикует доменное событие
- другой сервис реагирует на него
- делает локальный commit
- публикует следующее событие

То есть координация распределена между участниками процесса.

## 4. Flow 1: Claim creation -> async assessment

### Шаги

1. `POST /api/claims`
2. `claim-service`:
   - валидирует пользователей через `AuthRpcService.GetUser`
   - создаёт запись в `claims`
   - пишет timeline
   - пишет `CLAIM_CREATED` в `outbox_events`
3. `OutboxRelay` публикует `CLAIM_CREATED`
4. `assessment-service` получает событие:
   - dedup через `processed_messages`
   - создаёт `assessment_job`
5. `assessment-service` worker позже берёт `PENDING` job:
   - считает результат
   - пишет `ASSESSMENT_COMPLETED` в outbox
6. `claim-service` получает результат и переводит заявку:
   - в `NEED_ADDITIONAL_INFO`
   - или в `AWAITING_TENANT_RESPONSE`
   - или в `CLOSED_NO_PENALTY`

### Что здесь важно

- claim уже существует до завершения assessment
- это нормальное окно eventual consistency
- ни один глобальный rollback здесь не нужен

## 5. Flow 2: Penalty application success saga

### Шаги

1. `POST /api/claims/{id}/support-decision` с `applyPenalty=true`
2. `claim-service`:
   - ставит `PENALTY_PROCESSING`
   - пишет `PENALTY_APPLICATION_REQUESTED` в outbox
3. `penalty-service`:
   - создаёт `penalty_operation` со статусом `PENDING`
4. worker `penalty-service`:
   - переводит operation в `PROCESSING`
   - симулирует внешний процессор
   - при успехе переводит operation в `APPLIED`
   - пишет `PENALTY_APPLIED`
5. `claim-service`:
   - переводит claim в `PENALTY_APPLIED`
6. `auth-service`:
   - увеличивает `penaltyCount`
7. `notification-service` и `audit-service`:
   - записывают проекции

### Почему это сага

Потому что:

- claim finalization
- penalty execution
- user penalty counter update

находятся в разных bounded contexts и коммитятся независимо.

## 6. Flow 3: Penalty failure and manual recovery

### Шаги

1. Админ отправляет `support-decision` c `simulateFailure=true`
2. `claim-service` переводит claim в `PENALTY_PROCESSING`
3. `penalty-service` создаёт `penalty_operation`
4. worker переводит её в `FAILED`
5. `penalty-service` публикует `PENALTY_APPLICATION_FAILED`
6. `claim-service` переводит claim в `PENALTY_PROCESSING_FAILED`
7. Оператор вызывает `POST /api/penalties/operations/{id}/retry`
8. `penalty-service` возвращает operation в `PENDING`
9. следующий worker pass публикует `PENALTY_APPLIED`
10. claim и auth-service доходят до согласованного финала

### Это демонстрирует

- partial failure
- отсутствие глобального rollback
- recovery through explicit business action

## 7. Flow 4: User deactivation saga

### Шаги

1. `auth-service` деактивирует пользователя локально
2. пишет `USER_DEACTIVATED` в outbox
3. `claim-service` получает событие
4. находит незакрытые claims пользователя
5. закрывает их как `CLOSED_NO_PENALTY`
6. пишет timeline

### Это демонстрирует

- distributed reaction на бизнес-факт
- отсутствие общей транзакции между user state и claim state

## 8. Failure Handling Matrix

| Проблема | Как решается сейчас |
| --- | --- |
| DB commit прошёл, Kafka недоступна | событие остаётся в `outbox_events`, relay перепубликует позже |
| Kafka доставила событие дважды | consumer проверяет `processed_messages` |
| assessment ещё не успел обработать заявку | claim остаётся в `ASSESSMENT_IN_PROGRESS` |
| penalty processor упал | claim уходит в `PENALTY_PROCESSING_FAILED` |
| нужен recovery после penalty failure | есть manual retry endpoint |
| attachment привязан неатомарно с MinIO | `storage-service` ведёт `init -> confirm -> bind` saga |
| consumer не может обработать poison message | shared Kafka error handler отправляет запись в `<topic>.dlt` |
| долгий async шаг завис | scheduled timeout/reconciliation jobs переводят процесс в repair/failure state |
| нужна трассировка всей саги | использовать `audit-service` и `correlationId`/`sagaId` |

## 9. Что пока не реализовано

Для честности:

- нет schema registry
- нет отдельного monitoring UI для DLT/stuck saga
- нет production-grade distributed tracing stack

Но базовый каркас distributed transaction handling уже есть:

- outbox
- inbox
- explicit intermediate states
- manual recovery
- DLT publishing
- timeout/reconciliation jobs
- attachment saga

## 10. Что показывать на защите

### Сценарий 1

`ClaimCreated` сначала попадает в локальную БД, а потом уходит через outbox relay.

### Сценарий 2

После создания claim пользователь видит не финальный статус, а промежуточный `ASSESSMENT_IN_PROGRESS`.

### Сценарий 3

Penalty flow показывает `PENALTY_PROCESSING` и потом финализируется отдельным сервисом.

### Сценарий 4

Penalty failure переводит claim в `PENALTY_PROCESSING_FAILED`, после чего оператор восстанавливает процесс.

### Сценарий 5

`auth-service` деактивирует пользователя, а `claim-service` реагирует асинхронно и закрывает открытые claims.
