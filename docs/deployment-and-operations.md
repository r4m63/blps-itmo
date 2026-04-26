# Deployment, Runtime и Operations

## 1. Локальная инфраструктура

`docker-compose.yml` поднимает:

- `zookeeper`
- `kafka`
- `kafka-ui`
- `minio`
- `postgres-auth`
- `postgres-claim`
- `postgres-assessment`
- `postgres-penalty`
- `postgres-storage`
- `postgres-notification`
- `postgres-audit`

Это локальная demo-топология, а не production cluster.

## 2. Порты по умолчанию

### Сервисы

- `api-gateway` — `8080`
- `claim-service` — `8081`
- `auth-service` — `8082`
- `assessment-service` — `8083`
- `penalty-service` — `8084`
- `notification-service` — `8085`
- `audit-service` — `8086`
- `storage-service` — `8087`

### gRPC

- `claim-service` — `19081`
- `auth-service` — `19082`
- `penalty-service` — `19084`
- `audit-service` — `19086`
- `storage-service` — `19087`

### Infra

- `ZooKeeper` — `${ZOOKEEPER_PORT}` по умолчанию `2181`
- `Kafka internal host port` — `${KAFKA_PORT}` по умолчанию `9092`
- `Kafka external host port` — `${KAFKA_EXTERNAL_PORT}` по умолчанию `29092`
- `Kafka UI` — `${KAFKA_UI_PORT}` по умолчанию `8088`
- `MinIO API` — `${MINIO_PORT}` по умолчанию `9000`
- `MinIO Console` — `${MINIO_CONSOLE_PORT}` по умолчанию `9001`
- `postgres-auth` — `5433`
- `postgres-claim` — `5434`
- `postgres-assessment` — `5435`
- `postgres-penalty` — `5436`
- `postgres-notification` — `5437`
- `postgres-audit` — `5438`
- `postgres-storage` — `5439`

## 3. Конфигурация

Основной template:

- [.env.sample](../.env.sample)

Ключевые переменные:

- `ZOOKEEPER_PORT`
- `KAFKA_PORT`
- `KAFKA_EXTERNAL_PORT`
- `KAFKA_UI_PORT`
- `KAFKA_BOOTSTRAP_SERVERS`
- `MINIO_PORT`
- `MINIO_CONSOLE_PORT`
- `MINIO_ROOT_USER`
- `MINIO_ROOT_PASSWORD`
- `AUTH_DB_*`
- `CLAIM_DB_*`
- `ASSESSMENT_DB_*`
- `PENALTY_DB_*`
- `STORAGE_DB_*`
- `NOTIFICATION_DB_*`
- `AUDIT_DB_*`
- `*_SERVICE_PORT`
- `*_GRPC_PORT`
- `AUTH_GRPC_TARGET`
- `CLAIM_GRPC_TARGET`
- `PENALTY_GRPC_TARGET`
- `AUDIT_GRPC_TARGET`
- `STORAGE_GRPC_TARGET`

`*_SERVICE_PORT` нужны для HTTP edge/debug endpoints. Runtime sync-интеграция gateway и backend-сервисов использует `*_GRPC_TARGET`.

## 4. Порядок запуска

### Шаг 1. Инфраструктура

```bash
docker compose up -d
```

При первом старте каждого `postgres-*` контейнера официальный entrypoint выполнит соответствующий `sql/init_*_service.sql`, потому что эти файлы примонтированы в `/docker-entrypoint-initdb.d/`.

Важно:

- init-скрипты выполняются только при инициализации пустого data volume
- если volume уже существует, Postgres не переисполнит эти скрипты автоматически
- чтобы прогнать инициализацию заново, нужен новый volume, например через `docker compose down -v`

### Шаг 2. Сервисы

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

### Шаг 3. Проверка сборки

```bash
./gradlew build
```

## 5. Polling и background workers

### Outbox relay

Во всех сервисах включён `@EnableScheduling`, а `OutboxRelay` по умолчанию опрашивает outbox каждые `1000 ms`.

### Assessment worker

`assessment-service` забирает `PENDING` jobs каждые `2000 ms`.

### Penalty worker

`penalty-service` забирает `PENDING` operations каждые `2000 ms`.

Это и создаёт видимую асинхронность в demo.

## 6. Kafka operational view

### Топики

- `claim.events`
- `assessment.events`
- `penalty.events`
- `auth.events`
- `storage.events`
- `<topic>.dlt` for poison messages after retry exhaustion

### Consumer groups

- `claim-service`
- `assessment-service`
- `penalty-service`
- `storage-service`
- `auth-service`
- `notification-service`
- `audit-service`

### Практический эффект consumer groups

- внутри одного group событие обрабатывается одной репликой сервиса
- разные сервисы получают одну и ту же бизнес-событийную ленту независимо

## 7. Масштабирование

## 7.1. HTTP/gRPC services

Горизонтальное масштабирование возможно для:

- `claim-service`
- `auth-service`
- `storage-service`
- `notification-service`
- `audit-service`

Условия:

- shared DB на сервис
- stateless application layer
- отсутствие in-memory coordination
- gRPC target должен указывать на конкретный instance или на service discovery/load balancer

## 7.2. Worker services

Горизонтальное масштабирование возможно для:

- `assessment-service`
- `penalty-service`

Условия:

- достаточное число Kafka partitions
- дедупликация на уровне `processed_messages`
- идемпотентная реакция на событие

## 7.3. Узкие места текущей demo-архитектуры

- один Kafka broker
- один ZooKeeper
- одна БД на сервис без HA
- сервисы запускаются вне контейнеров

То есть масштабирование архитектурно заложено, но production-hardening пока не сделан.

## 8. Наблюдаемость

### Уже есть

- `audit-service` как бизнес-level trace store
- `Kafka UI`
- `auth-service:/actuator/healthz`

### Пока нет

- централизованные логи
- distributed tracing
- metrics stack
- alerts
- dead-letter monitoring

### MinIO status

`MinIO` присутствует в инфраструктуре, а `storage-service` владеет attachment metadata и ведёт saga
`init -> confirm -> bind`. По умолчанию `STORAGE_VERIFY_MINIO_OBJECT=false`, поэтому local demo может подтверждать
metadata без обязательного HEAD-check объекта в MinIO.

## 9. SQL и схема

В проекте есть явные SQL init/drop скрипты для каждого сервиса:

- `sql/init_auth_service.sql`
- `sql/init_claim_service.sql`
- `sql/init_assessment_service.sql`
- `sql/init_penalty_service.sql`
- `sql/init_storage_service.sql`
- `sql/init_notification_service.sql`
- `sql/init_audit_service.sql`

И соответствующие `drop_*`.

Важно:

- сейчас JPA работает с `ddl-auto=update`
- SQL-скрипты нужны как спецификация схемы и как источник истины для документации

## 10. Demo-операции

Для демонстрации использовать:

- [docs/lab3-runbook.md](lab3-runbook.md)
- `rest-client/scenarios/30-lab3-async-penalty.http`
- `rest-client/scenarios/31-lab3-penalty-failure-recovery.http`
- `rest-client/scenarios/32-lab3-user-deactivation.http`
- `rest-client/scenarios/33-lab3-attachment-saga.http`

## 11. Рекомендации по следующему развитию

Если проект будет развиваться дальше, логичные следующие шаги такие:

- вынести сервисы в отдельные контейнеры
- добавить `schema registry`
- добавить tracing/metrics stack
- усилить security perimeter до OAuth2/resource-server модели
- добавить DLT monitoring UI и alerts
