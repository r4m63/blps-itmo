# BLPS ITMO Lab3

`BLPS ITMO Lab3` — это event-driven микросервисная система на `Spring Boot 3.2`, `Java 17`, `PostgreSQL`, `Kafka`, `gRPC` и `ZooKeeper`, построенная вокруг обработки заявок на штраф между арендодателем, арендатором и администратором.

Проект демонстрирует не просто декомпозицию монолита на сервисы, а именно **System Design для распределённых транзакций**:

- `database per service`
- `HTTP API Gateway` для входных клиентских запросов
- `gRPC` для синхронных внутренних вызовов
- `Transactional Outbox`
- `processed_messages` как inbox/idempotency layer
- `Saga choreography`
- `eventual consistency`
- асинхронные worker-процессы для тяжёлых шагов
- ручное восстановление после частичного сбоя

## Что реализовано

Текущая кодовая база состоит из следующих модулей:

- `platform-core` — общие event contracts, outbox/inbox, relay, exception mapping
- `grpc-contracts` — protobuf-контракты внутренних синхронных API
- `api-gateway` — внешний HTTP edge service для `/api/**`
- `auth-service` — пользователи, роли, деактивация, счётчик штрафов
- `claim-service` — жизненный цикл заявки и timeline
- `assessment-service` — асинхронная оценка заявки
- `penalty-service` — асинхронное применение штрафа и retry failed operation
- `storage-service` — metadata attachments и saga `init -> confirm -> bind`
- `notification-service` — consumer-проекция уведомлений
- `audit-service` — аудит и event trail

Физическая структура репозитория:

- `lib/platform-core/` — общая инфраструктура Outbox/Kafka/gRPC/security
- `lib/grpc-contracts/` — protobuf и generated gRPC stubs
- `services/*` — все микросервисы

Инфраструктура:

- `Kafka`
- `ZooKeeper`
- `MinIO`
- отдельная `PostgreSQL` для каждого сервиса
- `Kafka UI` для демонстрации event backbone

## Архитектурные принципы

### 1. Нет глобальной XA-транзакции

Система **не использует** глобальный `2PC/XA` между микросервисами. Каждый сервис коммитит только свою локальную БД.

### 2. Outbox решает dual-write problem

Бизнес-изменение и запись события в `outbox_events` происходят в одной локальной транзакции. Дальше отдельный `OutboxRelay` публикует событие в Kafka.

### 3. Inbox/idempotency решает повторную доставку

Каждый consumer проверяет таблицу `processed_messages` и не повторяет side effects для одного и того же `eventId`.

### 4. Межсервисные процессы оформлены как Saga

Координация идёт через доменные события, а не через общий transaction manager. Сага в текущем проекте — **choreography-based**, без отдельного orchestration engine.

### 5. Eventual consistency является нормой

После команды пользователя разные сервисы могут временно видеть разные части процесса. Это ожидаемое поведение и часть дизайна.

## Сервисы и ответственность

| Сервис | Ответственность | Своя БД | Синхронный API | Kafka |
| --- | --- | --- | --- | --- |
| `api-gateway` | внешний HTTP API, JWT perimeter, HTTP-to-gRPC mapping | нет | HTTP external / gRPC clients | нет |
| `auth-service` | пользователи, роли, деактивация, `penaltyCount` | `blps_auth` | gRPC | consume `PENALTY_APPLIED`, produce `USER_DEACTIVATED` |
| `claim-service` | заявки, статусы, timeline, центральная бизнес-логика | `blps_claim` | gRPC | produce claim events, consume assessment/penalty/auth/storage events |
| `assessment-service` | async assessment jobs | `blps_assessment` | нет | consume claim events, produce `ASSESSMENT_COMPLETED` / `ASSESSMENT_FAILED` |
| `penalty-service` | async penalty operations | `blps_penalty` | gRPC для query/retry | consume `PENALTY_APPLICATION_REQUESTED`, produce penalty result events |
| `storage-service` | attachment metadata и MinIO object-key lifecycle | `blps_storage` | gRPC | consume `ATTACHMENT_BINDING_REQUESTED`, produce storage events |
| `notification-service` | notification log | `blps_notification` | нет | consume all domain topics |
| `audit-service` | audit trail и трассировка саг | `blps_audit` | gRPC | consume all domain topics |

## Ключевые паттерны распределённых транзакций

### Transactional Outbox

Используется во всех сервисах через `platform-core`:

- `OutboxService`
- `OutboxEvent`
- `OutboxRelay`
- `outbox_events`

### Inbox / Processed Messages

Используется во всех consumer’ах:

- `ProcessedMessageService`
- `ProcessedMessage`
- `processed_messages`

### Saga Choreography

В проекте реализованы как минимум три саги:

- `claim-lifecycle-{claimId}`
- `penalty-application-{claimId}`
- `user-deactivation-{userId}`

## Основные бизнес-потоки

### 1. Создание заявки и асинхронная оценка

`claim-service` сохраняет заявку, пишет `CLAIM_CREATED` в outbox, `assessment-service` асинхронно создаёт job и позже публикует `ASSESSMENT_COMPLETED`.

### 2. Финальное решение и применение штрафа

Администратор переводит заявку в `PENALTY_PROCESSING`, после чего `penalty-service` либо публикует `PENALTY_APPLIED`, либо `PENALTY_APPLICATION_FAILED`.

### 3. Ручное восстановление после сбоя

Если `penalty-service` симулирует ошибку внешнего процессора, claim переходит в `PENALTY_PROCESSING_FAILED`, а оператор вызывает `POST /api/penalties/operations/{id}/retry`.

### 4. Реакция на деактивацию пользователя

`auth-service` публикует `USER_DEACTIVATED`, `claim-service` асинхронно закрывает активные заявки этого пользователя.

## Быстрый старт

1. Скопировать `.env.sample` в `.env`
2. Поднять инфраструктуру:

```bash
docker compose up -d
```

3. Запустить сервисы:

```bash
./gradlew :api-gateway:bootRun
./gradlew :auth-service:bootRun
./gradlew :claim-service:bootRun
./gradlew :assessment-service:bootRun
./gradlew :penalty-service:bootRun
./gradlew :storage-service:bootRun
./gradlew :notification-service:bootRun
./gradlew :audit-service:bootRun
```

4. Прогнать HTTP-сценарии через `api-gateway` из `rest-client/scenarios`

## Документация

- [docs/architecture.md](docs/architecture.md) — общий System Design и архитектура по сервисам
- [docs/business-process.md](docs/business-process.md) — бизнес-процесс, BPMN mapping и state machine
- [docs/api-reference.md](docs/api-reference.md) — внешний HTTP API и внутренние gRPC контракты
- [docs/event-catalog.md](docs/event-catalog.md) — event envelope, топики, payloads, producers/consumers
- [docs/data-model.md](docs/data-model.md) — data ownership и схемы БД
- [docs/distributed-transactions.md](docs/distributed-transactions.md) — Saga, Outbox, Inbox, failure handling
- [docs/deployment-and-operations.md](docs/deployment-and-operations.md) — деплой, конфиг, scaling и эксплуатация
- [docs/lab3-runbook.md](docs/lab3-runbook.md) — краткий demo-runbook
- [docs/uml/README.md](docs/uml/README.md) — каталог UML диаграмм

## UML / Диаграммы

PlantUML-источники лежат в `docs/uml/`:

- `system-context.puml`
- `container-view.puml`
- `deployment-view.puml`
- `claim-service-components.puml`
- `claim-lifecycle-state.puml`
- `create-claim-assessment-sequence.puml`
- `penalty-success-sequence.puml`
- `penalty-failure-retry-sequence.puml`
- `user-deactivation-sequence.puml`
- `data-ownership-class-diagram.puml`

## Технологический стек

- `Java 17`
- `Spring Boot 3.2`
- `Spring Web`
- `Spring Data JPA`
- `Spring Kafka`
- `gRPC`
- `Protocol Buffers`
- `PostgreSQL 16`
- `Kafka 3.7`
- `ZooKeeper 3.9`
- `API Gateway`
- `JWT demo auth`
- `MinIO`
- `Gradle multi-module`

## Текущие ограничения

Это важно для честного описания текущего System Design:

- `API Gateway` реализован как внешний edge service для `/api/**`
- `JWT/login/security perimeter` реализованы в demo-режиме
- `storage-service` реализует attachment metadata saga поверх MinIO flow
- `DLT` publishing и retry/backoff настроены через общий Kafka error handler
- `schema registry` пока не реализован
- `Kafka` в `docker-compose` поднят как single broker для demo-сценария
- сервисы запускаются локально через Gradle, а не как отдельные docker images
- `ddl-auto=update` включён для удобства локальной разработки, а не как production-практика

## Назначение проекта

Этот репозиторий удобен как учебный пример для демонстрации:

- декомпозиции монолита на bounded contexts
- проектирования распределённых транзакций без XA
- построения event-driven бизнес-процесса
- наблюдения за eventual consistency, retry и manual recovery
