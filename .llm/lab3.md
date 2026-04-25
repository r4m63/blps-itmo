## Lab3 System Design: микросервисы + Kafka + демонстрация distributed transactions

### Кратко

Цель lab3: перестроить текущее приложение в event-driven микросервисную систему так, чтобы в ней были не только асинхронные задачи, но и
осмысленно спроектированные распределённые транзакции. Базовая идея: не тянуть глобальный ACID/XA между микросервисами, а показать
правильную микросервисную модель через:

- локальную транзакцию в каждом сервисе
- Transactional Outbox
- Kafka как транспорт
- Saga для межсервисных процессов
- Inbox / processed_messages для идемпотентности consumer’ов
- timeouts, retry, DLQ, reconciliation
- явные промежуточные статусы и eventual consistency

Ниже план уже встроен в архитектуру lab3 и отвечает на вопрос: как именно мы решаем и демонстрируем проблемы distributed transactions.

———

## 1. Целевая архитектура и роль distributed transactions

### Сервисы

Целевая схема:

- api-gateway
- auth-service
- claim-service
- storage-service
- assessment-service
- penalty-service
- notification-service
- audit-service

### Владение данными

У каждого сервиса своя БД:

- auth-db
- claim-db
- storage-db
- assessment-db
- penalty-db
- notification-db
- audit-db

### Главный архитектурный тезис

После декомпозиции:

- глобальной XA-транзакции между сервисами не будет
- текущая логика с Narayana/JTA остаётся как артефакт предыдущего этапа и как контраст для объяснения
- в микросервисной версии межсервисная координация строится через Saga + Outbox + Kafka + Idempotency

### Что именно мы демонстрируем

Lab3 должен показать три класса distributed transaction problems:

1. DB + broker dual write
   Решение: Transactional Outbox
2. межсервисный бизнес-процесс без глобального commit
   Решение: Saga с промежуточными статусами и компенсациями
3. повторная доставка / retry / partial failure / stale reads
   Решение: Inbox, processed_messages, idempotency, timeouts, DLQ, reconciliation, status polling

———

## 2. Ключевые distributed transaction flows

### Flow A. Создание заявки и запуск асинхронной оценки

Это основной “happy path with eventual consistency”.

#### Сервисы

- api-gateway
- claim-service
- assessment-service
- notification-service
- audit-service

#### Шаги

1. Клиент вызывает POST /api/claims через gateway.
2. claim-service в одной локальной транзакции:
    - сохраняет claim
    - ставит статус SUBMITTED
    - пишет claim_status_history
    - пишет событие ClaimCreated в outbox_events
3. HTTP ответ пользователю:
    - 201 Created или 202 Accepted
    - в payload сразу возвращать claimId и processingStatus=SUBMITTED
4. Отдельный outbox-relay в claim-service публикует ClaimCreated в Kafka topic claim.events.
5. assessment-service получает ClaimCreated, в своей локальной транзакции:
    - проверяет processed_messages
    - создаёт assessment_job
    - помечает событие как обработанное
6. Асинхронный worker внутри assessment-service выполняет оценку.
7. После завершения assessment-service в локальной транзакции:
    - сохраняет результат оценки
    - пишет AssessmentCompleted в свой outbox
8. claim-service получает AssessmentCompleted, в локальной транзакции:
    - проверяет dedup
    - меняет статус claim:
        - NEED_ADDITIONAL_INFO, если данных недостаточно
        - UNDER_ASSESSMENT или сразу AWAITING_TENANT_RESPONSE, если данные достаточны и есть основания
        - CLOSED_NO_PENALTY, если оснований нет
    - пишет новые history records
9. notification-service и audit-service читают те же события асинхронно.

#### Что демонстрируется

- нет глобальной транзакции между claim-service и assessment-service
- ClaimCreated может быть уже committed, а assessment result ещё нет
- claim временно живёт в промежуточном состоянии
- это нормальная eventual consistency, а не баг

———

### Flow B. Финальное решение и применение штрафа

Это главный flow для демонстрации распределённой транзакции с необратимым side effect.

#### Сервисы

- claim-service
- penalty-service
- auth-service
- notification-service
- audit-service

#### Шаги

1. Admin вызывает POST /api/claims/`idñ/support-decision.
2. claim-service в локальной транзакции:
    - валидирует текущий статус SUPPORT_REVIEW
    - если applyPenalty=false:
        - переводит claim в CLOSED_NO_PENALTY
        - пишет ClaimClosedWithoutPenalty в outbox
        - flow завершён
    - если applyPenalty=true:
        - переводит claim в PENALTY_PROCESSING
        - пишет PenaltyApplicationRequested в outbox
3. penalty-service получает PenaltyApplicationRequested, в локальной транзакции:
    - проверяет dedup
    - создаёт penalty_operation со статусом PENDING
    - помечает сообщение как обработанное
4. Worker penalty-service делает “внешнее применение штрафа”:
    - либо симулированный external adapter
    - либо локальный mock-connector
5. Если успешно:
    - penalty-service в локальной транзакции пишет PenaltyApplied в outbox
6. claim-service получает PenaltyApplied, в локальной транзакции:
    - проверяет dedup
    - переводит claim в PENALTY_APPLIED
    - пишет финальный history record
7. auth-service получает PenaltyApplied, в локальной транзакции:
    - увеличивает penalty_count
    - пишет PenaltyCountUpdated в outbox
8. notification-service отправляет уведомления.
9. audit-service пишет весь event trail.

#### Что демонстрируется

- решение admin уже принято, но штраф ещё может быть “в процессе”
- нельзя делать вид, что финальный commit для всех сервисов мгновенный
- внешний side effect не откатывается обычным rollback
- поэтому нужен промежуточный статус PENALTY_PROCESSING

———

### Flow C. Деактивация пользователя как distributed reaction

Это хороший flow на событие, затрагивающее несколько bounded context.

#### Сервисы

- auth-service
- claim-service
- notification-service
- audit-service

#### Шаги

1. Admin вызывает POST /api/auth/users/`idñ/deactivate.
2. auth-service в локальной транзакции:
    - помечает user disabled
    - пишет UserDeactivated в outbox
3. claim-service получает UserDeactivated, в локальной транзакции:
    - находит open claims, где этот user landlord/tenant
    - переводит их в CLOSED_NO_PENALTY
    - пишет history и ClaimsClosedDueToUserDeactivation
4. notification-service уведомляет стороны.
5. audit-service пишет событие и реакцию.

#### Что демонстрируется

- один бизнес-факт из auth-service запускает корректировку состояния в claim-service
- между сервисами нет общей транзакции
- но есть гарантируемый процесс доведения до согласованного состояния

———

### Flow D. Attachment flow как отдельная saga с MinIO вне XA

Это специально важно, потому что у проекта уже есть интеграция с MinIO.

#### Сервисы

- storage-service
- claim-service

#### Шаги

1. storage-service делает attachments/init
    - сохраняет metadata row uploaded=false
    - пишет outbox event AttachmentInitialized
2. Клиент загружает файл напрямую в MinIO
3. storage-service делает attachments/confirm
    - проверяет объект в MinIO
    - ставит uploaded=true
    - пишет AttachmentUploadedConfirmed
4. При создании claim клиент передаёт attachment refs
5. claim-service валидирует, что attachment ids/object keys подтверждены
    - лучше не прямым DB join
    - а через sync internal query в storage-service или локальный attachment-read model
6. claim-service сохраняет claim и пишет AttachmentBindingRequested
7. storage-service помечает attachment как BOUND_TO_CLAIM

#### Что демонстрируется

- MinIO нельзя включить в глобальную XA-транзакцию
- значит нужен saga-style flow: init -> upload -> confirm -> bind
- это хороший контраст с “идеей единого rollback”

———

## 3. Паттерны и конкретные реализации

### 3.1. Transactional Outbox

Каждый сервис, который публикует события, обязан иметь:

- таблицу outbox_events
- relay publisher
- статус события NEW / PUBLISHED / FAILED

Минимальные поля outbox_events:

- id
- event_id
- event_type
- aggregate_type
- aggregate_id
- payload_json
- correlation_id
- saga_id
- created_at
- published_at
- status
- retry_count

Использование:

- бизнес-изменение и запись outbox выполняются в одной локальной DB-транзакции
- публикация в Kafka идёт отдельным relay-процессом
- если Kafka недоступна, бизнес-данные уже сохранены, а событие будет допубликовано позже

Это и есть демонстрация решения dual write problem.

———

### 3.2. Inbox / processed_messages

Каждый consumer должен иметь:

- таблицу processed_messages

Минимальные поля:

- event_id
- consumer_name
- processed_at
- correlation_id
- result_status

Правило обработки:

1. пришло событие
2. consumer открывает локальную транзакцию
3. проверяет наличие (event_id, consumer_name)
4. если есть — считает duplicate и ничего не делает
5. если нет — выполняет бизнес-изменение
6. в той же транзакции пишет processed record
7. commit

Это обязательный слой для:

- claim-service
- penalty-service
- auth-service
- notification-service
- audit-service

———

### 3.3. Saga model

Здесь нужен choreography-first saga, без отдельного orchestration engine.

Правило:

- каждый сервис реагирует на доменное событие
- выполняет локальную транзакцию
- публикует следующее доменное событие
- переходы фиксируются как бизнес-статусы

Для главных процессов выделить saga_id:

- claim-lifecycle-saga
- penalty-application-saga
- user-deactivation-saga

Где хранить статус саги:

- достаточно в audit-service и в owning aggregate status
- отдельная saga_state таблица нужна только если хочешь более явную диспетчеризацию зависших процессов

———

### 3.4. Компенсации

Нужны не rollback, а business compensations.

#### Для penalty flow

Если PenaltyApplicationRequested ушёл, а penalty-service не смог выполнить применение:

- penalty-service публикует PenaltyApplicationFailed
- claim-service переводит claim в:
    - PENALTY_PROCESSING_FAILED, если нужен manual review
    - или обратно в SUPPORT_REVIEW, если хотим повторное решение
- notification-service шлёт уведомление внутреннему оператору

#### Для additional info / assessment

Если assessment не может завершиться:

- claim уходит в ASSESSMENT_FAILED или MANUAL_REVIEW_REQUIRED
- admin может вручную триггерить reassessment

#### Для attachment binding

Если claim создан, а binding attachment не завершён:

- reconciliation job в storage-service и claim-service сравнивает attachment refs и доводит bind до консистентного состояния

Важно:

- компенсация тоже идемпотентна
- компенсация тоже может ретраиться
- компенсация не обязана идеально “стереть прошлое”

———

### 3.5. Timeout handling

Для каждого long-running шага сразу задать timeout policy.

Рекомендуемые таймауты:

- assessment-service: если job не завершён за N минут -> ASSESSMENT_TIMEOUT
- penalty-service: если штраф не применён за N минут -> PENALTY_TIMEOUT
- tenant response: если ответа нет за SLA -> TENANT_RESPONSE_EXPIRED
- outbox publish: если событие не публикуется слишком долго -> alert + retry escalation

Поведение:

- timeout должен менять бизнес- или технический статус
- timeout должен публиковать событие
- timeout должен быть виден в audit trail

———

### 3.6. Retry и DLQ

Для Kafka consumers:

- ограниченный retry count
- exponential backoff
- jitter
- разделение transient/permanent errors
- DLT для poison messages

Нужные топики:

- claim.events
- claim.events.dlt
- penalty.events
- penalty.events.dlt
- и т.д.

Правило:

- retry только для идемпотентных handler’ов
- если retry исчерпан:
    - событие идёт в DLT
    - audit-service пишет failure
    - оператор видит stuck flow

———

### 3.7. Reconciliation / Repair

Нужен отдельный механизм доведения системы до консистентного состояния.

Добавить jobs:

- outbox-repair-job: ищет зависшие NEW/FAILED outbox events
- penalty-reconciliation-job: ищет claims в PENALTY_PROCESSING без финального результата дольше SLA
- assessment-reconciliation-job: ищет claims без результата оценки
- attachment-reconciliation-job: ищет confirmed attachments, не привязанные к claim, и наоборот

Админские repair-команды:

- replay event
- retry penalty
- re-run assessment
- close claim manually
- mark saga compensated

Это важный демонстрационный слой: он показывает, что distributed transactions в реальном мире требуют не только “happy path”.

———

## 4. Kafka design

### Топики

Рекомендуемая схема:

- claim.events
- storage.events
- assessment.events
- penalty.events
- auth.events
- notification.events
- audit.events

Для внутренних команд можно оставить:

- penalty.commands
- assessment.commands

Но для lab3 достаточно и domain-event style без выделенных command topics, если не хочется переусложнять.

### Partition key

Ключи:

- для claim-цепочки: claimId
- для user-цепочки: userId
- для penalty-цепочки: claimId

Это даст локальный порядок по одной заявке или пользователю.

### Формат события

Стандартизовать envelope:

- eventId
- eventType
- eventVersion
- occurredAt
- producerService
- correlationId
- traceId
- sagaId
- aggregateType
- aggregateId
- actorId
- payload

———

## 5. API и read model под eventual consistency

### Внешний API

Через api-gateway сохранить привычный REST UX, но не обещать мгновенную глобальную консистентность.

### Что надо изменить

Для async flows возвращать:

- 202 Accepted, если операция запускает долгий межсервисный процесс
- processingStatus
- correlationId
- resourceId

Добавить status/query endpoints:

- GET /api/claims/`idñ
- GET /api/claims/`idñ/process-status
- GET /api/claims/`idñ/timeline
- GET /api/penalties/`claimIdñ опционально

### Что демонстрируется

- write уже принят, но read-model может ещё не догнаться
- UI/REST client должен жить с состояниями:
    - SUBMITTED
    - ASSESSMENT_IN_PROGRESS
    - NEED_ADDITIONAL_INFO
    - PENALTY_PROCESSING
    - PENALTY_APPLIED
    - PENALTY_PROCESSING_FAILED

———

## 6. Пошаговый план внедрения в lab3

### Этап 1. Подготовка монорепо

- перевести проект в Gradle multi-module
- создать модули сервисов и shared contracts
- вынести event envelope и common Kafka config

### Этап 2. Kafka + ZooKeeper + новые БД

- добавить zookeeper, kafka, kafka-ui
- добавить postgres-контейнеры по сервисам
- прописать env vars и service networking
- включить healthchecks

### Этап 3. Auth и Gateway

- выделить auth-service
- перевести внешний доступ на api-gateway
- заменить Basic auth на JWT/claims
- внутренние сервисы доверяют claims и не ходят напрямую в auth DB

### Этап 4. Claim + Outbox

- выделить claim-service
- реализовать outbox_events
- реализовать outbox relay
- публиковать ClaimCreated, SupportDecisionMade, PenaltyApplicationRequested

### Этап 5. Assessment

- выделить assessment-service
- сделать async job processing через Kafka
- добавить AssessmentCompleted
- добавить timeout/retry

### Этап 6. Penalty

- выделить penalty-service
- реализовать PenaltyApplicationRequested -> PenaltyApplied/Failed
- добавить compensation path и PENALTY_PROCESSING¡_FAILED¿

### Этап 7. Storage saga

- выделить storage-service
- сохранить MinIO flow как saga
- убрать прямую attachment ownership из claim DB

### Этап 8. Notification + Audit

- добавить notification-service и audit-service
- подписать их на основные доменные события
- собирать полный audit trail по correlationId/sagaId

### Этап 9. Inbox, DLT, Reconciliation

- внедрить processed_messages
- настроить retry + DLT
- добавить reconciliation jobs
- добавить admin repair commands

———

## 7. Демонстрационные сценарии для защиты lab3

### Сценарий 1. Dual write problem solved

Показать:

- claim сохранён
- Kafka временно недоступна
- событие не потеряно, потому что лежит в outbox
- после восстановления Kafka relay допубликовал сообщение

Это основной demo для Transactional Outbox.

### Сценарий 2. Eventual consistency

Показать:

- claim создан
- GET /claims/`idñ сначала показывает ASSESSMENT_IN_PROGRESS
- через некоторое время приходит AssessmentCompleted
- статус обновляется

Это основной demo для eventual consistency и lagging read model.

### Сценарий 3. Duplicate event handling

Показать:

- одно и то же PenaltyApplied или AssessmentCompleted доставляется дважды
- consumer не делает побочный эффект повторно
- processed_messages фиксирует dedup

Это demo для Inbox / idempotency.

### Сценарий 4. Penalty failure + compensation/manual recovery

Показать:

- admin принял решение о штрафе
- claim перешёл в PENALTY_PROCESSING
- penalty-service падает или даёт ошибку
- claim уходит в PENALTY_PROCESSING_FAILED
- operator делает retry или manual resolve

Это demo для Saga failure, compensation, repair.

### Сценарий 5. User deactivation distributed reaction

Показать:

- user деактивирован в auth-service
- claim-service асинхронно закрывает его открытые claims
- notification/audit фиксируют процесс

Это demo для межсервисной бизнес-реакции без глобальной транзакции.

### Сценарий 6. MinIO outside XA

Показать:

- attachment confirm и claim binding происходят не одной глобальной транзакцией
- но система всё равно доходит до корректного состояния
- при сбое работает reconciliation

Это demo, что внешний resource не участвует в XA.

———

## 8. Важные изменения интерфейсов и статусов

### Новые технические статусы

Рекомендуется добавить:

- ASSESSMENT_IN_PROGRESS
- ASSESSMENT_FAILED
- PENALTY_PROCESSING
- PENALTY_PROCESSING_FAILED
- MANUAL_REVIEW_REQUIRED

### Новые технические таблицы почти в каждом сервисе

- outbox_events
- processed_messages
- service-specific business tables
- при необходимости job_execution / repair_actions

### Новые API для эксплуатации

- GET /process-status/`claimIdñ
- POST /repair/claims/`idñ/retry-penalty
- POST /repair/claims/`idñ/reassess
- GET /audit/claims/`idñ/events

———

## 9. Принятые решения и допущения

- Архитектура: monorepo multi-module.
- Координация: Saga choreography, не workflow engine.
- Консистентность: eventual consistency, а не глобальный ACID.
- Надёжность публикации: Transactional Outbox.
- Надёжность потребления: Inbox / processed_messages.
- Kafka используется не для “галочки”, а в ключевых бизнес-процессах.
- ZooKeeper входит в Kafka-стек по условию задания.
- Текущая JTA/XA между двумя Postgres используется как историческая база проекта и как контраст с новой микросервисной моделью, но не как
  основной механизм lab3.
- Для защиты lab3 обязательно показать минимум 4 demo: outbox, eventual consistency, duplicate handling, failure/repair flow.
