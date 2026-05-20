# Отчет: Saga, Outbox и Inbox в трех микросервисах

## 1. Общая схема

В текущем проекте распределенная бизнес-транзакция реализована без общего XA/2PC coordinator. Вместо одной глобальной транзакции используется цепочка локальных транзакций в трех сервисах:

| Сервис | Собственная БД | Роль в процессе |
|---|---|---|
| `claim-service` | `blps_claim` | Владеет заявками, статусами, историей, таблицей `saga_instances`; запускает и завершает сагу применения штрафа. |
| `penalty-service` | `blps_penalty` | Владеет операцией штрафа `penalty_operations`; применяет или отклоняет штраф, умеет компенсировать через revoke. |
| `auth-service` | `blps_identity` | Владеет пользователями; при успешном штрафе увеличивает `penalty_count`, при компенсации уменьшает счетчик. |

Kafka используется как транспорт событий между сервисами. У каждого сервиса есть собственные таблицы:

- `outbox_events` - локальная очередь событий, которые надо опубликовать в Kafka;
- `processed_messages` - inbox/dedup-таблица уже обработанных Kafka-событий;
- только у `claim-service`: `saga_instances` - состояние saga state machine.

Ключевые топики:

- `penalty.events` - события применения/отката штрафа;
- `identity.events` - события пользователей и подтверждение, что штраф учтен в профиле пользователя;
- `claim.events` - события жизненного цикла claim.

## 2. Что именно является сагой

Главная межсервисная сага здесь - `PENALTY_APPLICATION`.

Она начинается в `claim-service`, когда support/admin принимает решение применить штраф по claim:

1. `claim-service` переводит claim в `PENALTY_PROCESSING`;
2. создает строку в `saga_instances`;
3. записывает `current_saga_id` в claim;
4. кладет в `outbox_events` событие `PENALTY_APPLICATION_REQUESTED` в топик `penalty.events`.

Все это выполняется в одной локальной транзакции `claim-service`.

Дальше saga идет по событиям:

```text
claim-service
  SUPPORT_REVIEW -> PENALTY_PROCESSING
  saga = AWAITING_PENALTY_APPLIED
  outbox: PENALTY_APPLICATION_REQUESTED

Kafka: penalty.events

penalty-service
  penalty_operation: PENDING -> PROCESSING -> APPLIED
  outbox: PENALTY_APPLIED

Kafka: penalty.events

claim-service
  saga: AWAITING_PENALTY_APPLIED -> AWAITING_PENALTY_COUNTED

auth-service
  user.penalty_count++
  outbox: PENALTY_COUNTED

Kafka: identity.events

claim-service
  saga: AWAITING_PENALTY_COUNTED -> COMPLETED
  claim: PENALTY_PROCESSING -> PENALTY_APPLIED
```

Финальный статус `PENALTY_APPLIED` ставится не сразу после ответа `penalty-service`, а только после события `PENALTY_COUNTED` от `auth-service`. Это важно: для проекта успешная saga означает не только "штраф создан", но и "штраф учтен у пользователя".

## 3. State machine saga

Состояния описаны в `SagaState`:

| Состояние | Значение |
|---|---|
| `AWAITING_PENALTY_APPLIED` | Claim уже в `PENALTY_PROCESSING`, ждем результата от `penalty-service`. |
| `AWAITING_PENALTY_COUNTED` | Штраф применен, ждем, пока `auth-service` увеличит `penalty_count`. |
| `COMPLETED` | Штраф применен и учтен у пользователя; claim закрыт как `PENALTY_APPLIED`. |
| `PENALTY_FAILED` | `penalty-service` не смог применить штраф; claim переходит в `PENALTY_PROCESSING_FAILED`. |
| `COMPENSATING_REVOKE` | Запущена компенсация: надо отозвать штраф в `penalty-service`. |
| `COMPENSATED` | Компенсация завершена, штраф отозван. |
| `COMPENSATION_FAILED` | Компенсация не прошла после максимального числа попыток. |

Таблица `saga_instances` хранит:

- `saga_id` - сквозной идентификатор saga;
- `claim_id` - claim, к которому привязана saga;
- `state` - текущее состояние;
- `attempt_count` / `max_attempts` - попытки компенсации;
- `failure_reason`;
- `started_at`, `last_event_at`, `completed_at`.

`SagaTimeoutWatcher` раз в несколько секунд ищет зависшие saga в состояниях `AWAITING_PENALTY_APPLIED`, `AWAITING_PENALTY_COUNTED`, `COMPENSATING_REVOKE` и переводит их в компенсацию через `PENALTY_REVOKE_COMMAND`.

## 4. Transactional Outbox

Outbox решает проблему dual write: нельзя сначала записать бизнес-данные в БД, а потом отдельно отправлять Kafka-событие напрямую из бизнес-кода. Между этими действиями процесс может упасть.

В проекте правило такое:

```text
@Transactional
1. Изменить бизнес-состояние в своей БД.
2. INSERT в outbox_events со статусом NEW.
3. COMMIT.

Отдельно:
4. OutboxRelay читает NEW события.
5. Публикует EventEnvelope в Kafka.
6. Помечает outbox_events.status = PUBLISHED.
```

Примеры:

- `ClaimService.supportDecision(...)` меняет claim, создает saga и пишет `PENALTY_APPLICATION_REQUESTED`;
- `PenaltyService.applyInternal(...)` переводит penalty operation в `APPLIED` и пишет `PENALTY_APPLIED`;
- `PenaltyService.failInternal(...)` переводит operation в `FAILED` и пишет `PENALTY_APPLICATION_FAILED`;
- `auth-service` при `PENALTY_APPLIED` увеличивает `penalty_count` и пишет `PENALTY_COUNTED`;
- `UserService.deactivate(...)` выключает пользователя и пишет `USER_DEACTIVATED`.

Таблица `outbox_events` есть во всех трех БД и содержит `id`, `aggregate_type`, `aggregate_id`, `event_type`, `topic`, `payload`, `status`, `retry_count`, `last_error`, `saga_id`, `correlation_id`, `created_at`, `published_at`.

## 5. Inbox / processed messages

Kafka дает как минимум once-delivery, поэтому consumer должен быть идемпотентным. Для этого в каждом сервисе есть таблица `processed_messages`:

```text
event_id UUID PRIMARY KEY
processed_at TIMESTAMPTZ
```

Логика consumer'а:

```text
@Transactional
1. Прочитать EventEnvelope.
2. Проверить processed_messages по event_id.
3. Если событие уже есть - выйти без бизнес-эффекта.
4. Выполнить бизнес-изменение.
5. Записать event_id в processed_messages.
6. COMMIT.
```

Примеры:

- `penalty-service` слушает `PENALTY_APPLICATION_REQUESTED` и `PENALTY_REVOKE_COMMAND`;
- `claim-service` слушает `PENALTY_APPLIED`, `PENALTY_APPLICATION_FAILED`, `PENALTY_REVOKED`, `PENALTY_REVOKE_FAILED`, `PENALTY_COUNTED`, `USER_DEACTIVATED`;
- `auth-service` слушает `PENALTY_APPLIED` и `PENALTY_REVOKED`.

Если consumer упал после commit БД, но до commit Kafka offset, событие придет повторно. При повторной доставке `processed_messages` уже содержит `event_id`, поэтому side-effect не повторяется.

## 6. Как устроена локальная транзакция по шагам

### Шаг 1. Support применяет штраф

Метод `ClaimService.supportDecision(...)` помечен `@Transactional`.

Внутри одной транзакции:

1. проверяется, что claim в `SUPPORT_REVIEW`;
2. claim переводится в `PENALTY_PROCESSING`;
3. создается запись истории статуса;
4. создается `SagaInstance` со state `AWAITING_PENALTY_APPLIED`;
5. `claim.current_saga_id` получает `saga_id`;
6. создается outbox-событие `PENALTY_APPLICATION_REQUESTED`;
7. commit.

Если commit не прошел, не останется ни статуса claim, ни saga row, ни outbox row. Если commit прошел, событие гарантированно лежит в outbox и будет пытаться уйти в Kafka.

### Шаг 2. `penalty-service` применяет штраф

Consumer `PenaltyEventConsumer` обрабатывает событие в `@Transactional` методе.

Внутри одной транзакции:

1. проверяет inbox по `event_id`;
2. создает или находит `PenaltyOperation` по `claim_id`;
3. сохраняет `last_saga_id`;
4. переводит operation `PENDING -> PROCESSING -> APPLIED`;
5. пишет outbox-событие `PENALTY_APPLIED`;
6. пишет `event_id` в `processed_messages`;
7. commit.

Если включен `simulateFailure`, operation переводится в `FAILED`, а в outbox пишется `PENALTY_APPLICATION_FAILED`.

### Шаг 3. `claim-service` получает `PENALTY_APPLIED`

Внутри одной транзакции:

1. проверяет inbox;
2. находит saga по `saga_id`;
3. проверяет, что state сейчас `AWAITING_PENALTY_APPLIED`;
4. переводит saga в `AWAITING_PENALTY_COUNTED`;
5. пишет `event_id` в `processed_messages`;
6. commit.

Claim еще не закрывается как `PENALTY_APPLIED`, потому что нужно дождаться, что пользовательский счетчик штрафов обновлен в `auth-service`.

### Шаг 4. `auth-service` учитывает штраф у пользователя

Внутри одной транзакции:

1. проверяет inbox;
2. увеличивает `user.penalty_count`;
3. пишет outbox-событие `PENALTY_COUNTED` в `identity.events`;
4. пишет `event_id` в `processed_messages`;
5. commit.

### Шаг 5. `claim-service` завершает saga

Когда приходит `PENALTY_COUNTED`, `claim-service`:

1. проверяет inbox;
2. находит saga;
3. проверяет state `AWAITING_PENALTY_COUNTED`;
4. переводит saga в `COMPLETED`;
5. переводит claim `PENALTY_PROCESSING -> PENALTY_APPLIED`;
6. выставляет `closed_at`;
7. пишет историю статуса;
8. пишет `event_id` в `processed_messages`;
9. commit.

## 7. Компенсация

Компенсация здесь не является rollback транзакции. Это отдельное бизнес-действие.

Если `penalty-service` возвращает `PENALTY_APPLICATION_FAILED`:

- saga переходит в `PENALTY_FAILED`;
- claim переходит в `PENALTY_PROCESSING_FAILED`;
- процесс завершается как неуспешный.

Если saga зависла и сработал timeout:

1. `SagaTimeoutWatcher` вызывает `triggerCompensation(...)`;
2. saga переводится в `COMPENSATING_REVOKE`;
3. `claim-service` пишет outbox-событие `PENALTY_REVOKE_COMMAND`;
4. `penalty-service` обрабатывает команду и переводит operation в `REVOKED`;
5. `penalty-service` публикует `PENALTY_REVOKED`;
6. `auth-service`, если штраф был применен, уменьшает `penalty_count`;
7. `claim-service` переводит saga в `COMPENSATED`.

Если revoke не проходит, `onPenaltyRevokeFailed(...)` увеличивает `attempt_count`; после `max_attempts` saga становится `COMPENSATION_FAILED`.

## 8. Гарантии

### Что гарантируется

1. Локальная ACID-транзакция внутри каждого сервиса.

   Сервис атомарно меняет свои таблицы: бизнес-таблицы, outbox, inbox и saga-state в своей БД.

2. Нет dual write между бизнес-БД и outbox.

   Бизнес-изменение и запись события в `outbox_events` происходят в одной транзакции.

3. Повторная доставка Kafka не должна повторять бизнес-эффект.

   `processed_messages.event_id` является `PRIMARY KEY`; повторное событие отсекается по `event_id`.

4. События связываются с saga через `saga_id`.

   `saga_id` пишется в outbox и попадает в `EventEnvelope`, поэтому следующие сервисы могут продолжать конкретный экземпляр saga.

5. Порядок событий для одного aggregate задается Kafka key.

   Outbox relay отправляет сообщение с key = `aggregate_id`, поэтому события одного claim/operation попадают в одну partition при корректной настройке топика.

6. Есть recovery для зависших saga.

   `SagaTimeoutWatcher` переводит зависшие промежуточные состояния в компенсацию.

### Что не гарантируется

1. Нет глобального atomic commit на три БД.

   `claim-service`, `penalty-service` и `auth-service` коммитят независимо. Между шагами система может быть в промежуточном состоянии.

2. Нет строгого exactly-once end-to-end.

   Система рассчитана на at-least-once доставку плюс идемпотентные consumer'ы.

3. Outbox relay в текущем коде не дожидается Kafka ack.

   `KafkaTemplate.send(...)` вызывается, после чего событие сразу помечается `PUBLISHED`. Если асинхронная отправка фактически упадет позже, текущий код может считать событие опубликованным. Для более строгой гарантии надо ждать `CompletableFuture`/callback и помечать `PUBLISHED` только после успешного ack от broker.

4. Inbox сейчас хранит только `event_id`, без `consumer_name`.

   Для текущих обработчиков этого достаточно, пока один сервис не должен независимо обработать одно и то же событие несколькими разными handlers в одной БД. Если таких handlers станет несколько, нужен ключ `(event_id, consumer_name)`.

5. Внешние side effects не откатываются обычным rollback.

   Любое уже выполненное действие компенсируется новым бизнес-событием, а не откатом старой транзакции.

## 9. Итоговая формулировка для защиты

В проекте распределенная транзакция реализована как saga choreography поверх Kafka. Каждый микросервис владеет своей БД и выполняет только локальные ACID-транзакции. Чтобы не потерять события при падении между записью в БД и Kafka, используется Transactional Outbox: бизнес-изменение и событие сохраняются в одной транзакции, а отдельный relay публикует событие позже. Чтобы повторная доставка Kafka не приводила к повторным side effects, consumer'ы используют Inbox через таблицу `processed_messages`.

Сага применения штрафа начинается в `claim-service`, продолжается в `penalty-service`, затем в `auth-service`, и завершается обратно в `claim-service`. Успех фиксируется только когда штраф применен и счетчик пользователя обновлен. При ошибке или timeout запускается компенсация через `PENALTY_REVOKE_COMMAND`, а финальное состояние saga сохраняется в `saga_instances`.

Архитектурная гарантия здесь не "один глобальный commit", а eventual consistency: каждый шаг надежно фиксируется локально, события доставляются как минимум один раз, дубликаты отсекаются inbox-паттерном, а незавершенные процессы добиваются timeout watcher'ом и компенсационными действиями.

## 10. Где это смотреть в коде

| Что проверять | Файл |
|---|---|
| Старт penalty saga из бизнес-операции support decision | `services/claim-service/src/main/java/blps/itmo/claim/service/ClaimService.java` |
| State machine saga, forward-переходы и компенсация | `services/claim-service/src/main/java/blps/itmo/claim/saga/PenaltyApplicationSaga.java` |
| Поиск зависших saga и запуск компенсации | `services/claim-service/src/main/java/blps/itmo/claim/saga/SagaTimeoutWatcher.java` |
| Consumer событий `penalty.events` и `identity.events` в claim-service | `services/claim-service/src/main/java/blps/itmo/claim/kafka/ClaimEventConsumer.java` |
| Обработка `PENALTY_APPLICATION_REQUESTED` и `PENALTY_REVOKE_COMMAND` | `services/penalty-service/src/main/java/blps/itmo/penalty/kafka/PenaltyEventConsumer.java` |
| Создание, применение, fail и revoke penalty operation | `services/penalty-service/src/main/java/blps/itmo/penalty/service/PenaltyService.java` |
| Учет `penalty_count` и публикация `PENALTY_COUNTED` | `services/auth-service/src/main/java/blps/itmo/auth/kafka/PenaltyEventConsumer.java` |
| Деактивация пользователя и событие `USER_DEACTIVATED` | `services/auth-service/src/main/java/blps/itmo/auth/service/UserService.java` |
| Общая форма outbox producer'а | `*/kafka/outboxevent/OutboxService.java` |
| Outbox relay в Kafka | `*/kafka/OutboxRelay.java` |
| Inbox/dedup | `*/kafka/processedmessage/ProcessedMessageService.java` |
| DDL таблиц `outbox_events`, `processed_messages`, `saga_instances` | `sql/init_claim_service.sql`, `sql/init_penalty_service.sql`, `sql/init_auth_service.sql` |

Примечание: в `auth-service` Kafka consumer использует `groupId = "edge-service"`. Это выглядит как оставшееся имя группы от прежней архитектуры, но код физически находится и выполняется в `auth-service`.
