# Deployment and Operations

## 1. Локальная инфраструктура

`docker-compose.yml` поднимает:

- `zookeeper`
- `kafka` (single broker)
- `kafka-ui`
- `minio` (single node)
- `postgres-identity` (для edge-service)
- `postgres-claim`
- `postgres-penalty`

Это **локальная demo-топология**, не production cluster.

## 2. Порты

### Сервисы

| Сервис | HTTP | gRPC |
|---|---|---|
| `edge-service` | `8080` (внешний edge) + `8082` (direct debug) | `19082` |
| `claim-service` | `8081` (direct debug) | `19081` |
| `penalty-service` | `8084` (direct debug) | `19084` |

Внешние клиенты ходят **только** на `8080` (edge-service). Порты 8081/8084 — для локальной отладки backend-сервисов в обход edge.

### Инфраструктура (defaults, переопределяются через `.env`)

| Компонент | Port env var | Default |
|---|---|---|
| ZooKeeper | `ZOOKEEPER_PORT` | `2181` |
| Kafka broker (internal) | `KAFKA_PORT` | `9092` |
| Kafka broker (host) | `KAFKA_EXTERNAL_PORT` | `29092` |
| Kafka UI | `KAFKA_UI_PORT` | `8088` |
| MinIO API | `MINIO_PORT` | `9000` |
| MinIO Console | `MINIO_CONSOLE_PORT` | `9001` |
| postgres-identity | `IDENTITY_DB_PORT` | `5433` |
| postgres-claim | `CLAIM_DB_PORT` | `5434` |
| postgres-penalty | `PENALTY_DB_PORT` | `5435` |

## 3. Конфигурация

### `.env`

```bash
# Kafka
ZOOKEEPER_PORT=2181
KAFKA_PORT=9092
KAFKA_EXTERNAL_PORT=29092
KAFKA_UI_PORT=8088
KAFKA_BOOTSTRAP_SERVERS=localhost:29092

# MinIO
MINIO_PORT=9000
MINIO_CONSOLE_PORT=9001
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin

# Postgres per service
IDENTITY_DB_PORT=5433
IDENTITY_DB_NAME=blps_identity
IDENTITY_DB_USER=blps
IDENTITY_DB_PASSWORD=blps

CLAIM_DB_PORT=5434
CLAIM_DB_NAME=blps_claim
CLAIM_DB_USER=blps
CLAIM_DB_PASSWORD=blps

PENALTY_DB_PORT=5435
PENALTY_DB_NAME=blps_penalty
PENALTY_DB_USER=blps
PENALTY_DB_PASSWORD=blps

# Service ports
EDGE_SERVICE_PORT=8080
EDGE_DEBUG_PORT=8082
EDGE_GRPC_PORT=19082

CLAIM_SERVICE_PORT=8081
CLAIM_GRPC_PORT=19081

PENALTY_SERVICE_PORT=8084
PENALTY_GRPC_PORT=19084

# gRPC targets (edge-service → backends)
CLAIM_GRPC_TARGET=localhost:19081
PENALTY_GRPC_TARGET=localhost:19084

# JWT
JWT_SECRET=demo-secret-replace-in-prod
JWT_TTL_SECONDS=3600

# MinIO bucket
MINIO_BUCKET=attachments
MINIO_URL=http://localhost:9000
```

Шаблон в [.env.sample](../.env.sample), копировать и подправить под себя.

## 4. Порядок запуска

### Шаг 1. Инфраструктура

```bash
cp .env.sample .env   # отредактировать если нужно
docker compose up -d
```

При первом старте Postgres-контейнеры создаются пустыми. **Flyway** в каждом сервисе применит миграции при первом `bootRun`.

Полная переинициализация (сбросить volumes):

```bash
docker compose down -v
docker compose up -d
```

### Шаг 2. Сервисы

Каждый в отдельном терминале:

```bash
./gradlew :edge-service:bootRun
./gradlew :claim-service:bootRun
./gradlew :penalty-service:bootRun
```

При первом запуске сервис применяет Flyway migrations к своей БД и seed'ит demo-данные (только edge-service сидит 6 пользователей).

### Шаг 3. Smoke check

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"userId": 1}'
```

### Сборка и тесты

```bash
./gradlew build              # все модули
./gradlew :claim-service:test
./gradlew :claim-service:integrationTest   # Testcontainers
```

## 5. Polling и background workers

### Outbox relay (во всех 3 сервисах)

`@Scheduled(fixedDelay=1000)` в `OutboxRelay` (из `platform-core`):

- забирает 50 events со `status=NEW` или `status=FAILED AND retry_count < 5`
- публикует в Kafka
- помечает PUBLISHED или инкрементит retry_count

### Assessment worker (в claim-service)

`@Scheduled(fixedDelay=2000)` забирает `assessment_jobs` со `status=PENDING`, обрабатывает по 1 за tick, обновляет статус и пишет outbox-event.

### Penalty worker (в penalty-service)

`@Scheduled(fixedDelay=2000)` забирает `penalty_operations` со `status=PENDING`, имитирует внешний процессор, обновляет статус, пишет outbox-event.

### Tenant timeout job (в claim-service)

`@Scheduled(fixedDelay=60000)` — раз в минуту:

```sql
SELECT id FROM claims
WHERE status = 'AWAITING_TENANT_RESPONSE'
  AND now() - updated_at > interval '3 days'
FOR UPDATE SKIP LOCKED LIMIT 50;
```

Переводит в `SUPPORT_REVIEW`.

### Orphan attachment reconciliation (в claim-service)

`@Scheduled(fixedDelay=3600000)` — раз в час:

- `INITIALIZED`/`CONFIRMED` без `claim_id` старше 24h → `ORPHANED`, удалить из MinIO

## 6. Kafka

### Топики

При первом подключении продьюсера broker создаёт топики автоматически (`auto.create.topics.enable=true`). Для prod нужны явные `kafka-topics --create`.

| Topic | Partitions | Replication |
|---|---|---|
| `claim.events` | 3 | 1 (demo) |
| `penalty.events` | 3 | 1 |
| `identity.events` | 1 | 1 |
| `*.dlt` | 1 | 1 |

### Consumer groups

- `claim-service` consumer group: подписан на `claim.events` (self-loop), `penalty.events`, `identity.events`
- `penalty-service` consumer group: подписан на `penalty.events`
- `edge-service` consumer group: подписан на `penalty.events` (только `PENALTY_APPLIED` для penalty_count++)

### Внутри одного consumer group событие обрабатывается одной репликой сервиса. Разные сервисы получают одну и ту же ленту независимо (каждый в своём group).

## 7. MinIO

### Bucket setup

При первом запуске `claim-service` создаёт bucket `attachments`, если он не существует:

```java
if (!minioClient.bucketExists(BucketExistsArgs.of("attachments"))) {
    minioClient.makeBucket(MakeBucketArgs.of("attachments"));
}
```

### Presigned URLs

`POST /api/attachments/init` возвращает presigned PUT URL (TTL 1 час). Клиент загружает прямо в MinIO, минуя claim-service.

### HEAD-check при confirm

`POST /api/attachments/{id}/confirm` делает `statObject(...)` **до** открытия @Transactional. Если объект отсутствует — 409 Conflict.

### Verify mode

Env var `STORAGE_VERIFY_MINIO_OBJECT=false` (default) — отключает HEAD-check (для demo, чтобы можно было confirm без реальной загрузки).

## 8. Observability (минимум)

### Health checks

Каждый сервис экспонирует:

- `/actuator/health` — стандартный Spring Boot, проверяет DB + Kafka + (для claim) MinIO
- `/actuator/health/liveness` — процесс жив
- `/actuator/health/readiness` — готов принимать трафик
- Custom indicator `outboxBacklog` — `WARN` если есть NEW events старше 5 минут

### Structured logs

`logback-spring.xml` настроен на JSON-output с полями:

- `timestamp`, `level`, `logger`, `message`
- `correlationId` (из MDC)
- `sagaId` (из MDC, если есть)
- `eventId` (если обработка event'а)
- `claimId`, `userId` (где применимо)

### Kafka UI

`http://localhost:8088` — обзор топиков, consumer lag, ручная публикация для duplicate-event теста.

## 9. Масштабирование

### Горизонтально (production patterns)

| Сервис | Условия |
|---|---|
| `edge-service` | stateless, JWT shared secret, load balancer перед репликами |
| `claim-service` | stateless app layer; optimistic locking защищает от race условий; Kafka consumer group распределяет partition'ы |
| `penalty-service` | то же, worker scaling через partition count |

### Bottlenecks текущего demo

- single Kafka broker (replication factor=1)
- single ZooKeeper
- одна Postgres на сервис без HA / read replicas
- single MinIO node
- сервисы запускаются вне контейнеров

Масштабирование архитектурно заложено (stateless services + per-key partitioning + idempotent consumers), но production hardening — future work.

## 10. SQL / схема

Используется **Flyway** в каждом сервисе:

```
services/edge-service/src/main/resources/db/migration/V*.sql
services/claim-service/src/main/resources/db/migration/V*.sql
services/penalty-service/src/main/resources/db/migration/V*.sql
```

`spring.jpa.hibernate.ddl-auto=validate` — Hibernate сравнивает entity со схемой, но не меняет её. Любое изменение модели = Flyway migration.

## 11. Команды для частых задач

```bash
# Логи конкретного сервиса (если запущен через bootRun, смотри терминал)
./gradlew :claim-service:bootRun --console=plain

# Подключиться к Postgres сервиса
docker compose exec postgres-claim psql -U blps -d blps_claim

# Посмотреть outbox в claim-service
docker compose exec postgres-claim psql -U blps -d blps_claim -c \
  "SELECT id, event_type, status, retry_count, error_message FROM outbox_events ORDER BY id DESC LIMIT 20;"

# Посмотреть processed_messages в claim-service
docker compose exec postgres-claim psql -U blps -d blps_claim -c \
  "SELECT event_id, consumer_name, processed_at FROM processed_messages ORDER BY processed_at DESC LIMIT 20;"

# Опубликовать тестовое событие в Kafka (для duplicate-handling demo)
docker compose exec kafka kafka-console-producer \
  --bootstrap-server kafka:9092 --topic penalty.events --property "key.separator=:" --property "parse.key=true"
# затем ввести: claimId:{"eventId":"...","eventType":"PENALTY_APPLIED",...}

# Очистить локальные Kafka-данные
docker compose down -v
```

## 12. Demo operations

См. [runbook.md](runbook.md) для пошаговых demo-сценариев и `.http` файлы в `rest-client/scenarios/`.

## 13. Рекомендации для следующего шага (за рамками lab3)

При развитии в более серьёзный вид:

- упаковать сервисы в Docker images, добавить multi-stage Dockerfile в каждый сервис
- Helm-чарты для Kubernetes
- schema registry (Confluent / Apicurio) для Kafka payload'ов
- OpenTelemetry + Jaeger / Tempo для distributed tracing
- Prometheus + Grafana для metrics
- централизованные логи (Loki / ELK)
- DLT monitoring UI с alerts
- OAuth2 / OIDC вместо demo JWT
- mTLS между сервисами через service mesh (Istio / Linkerd)
- Kafka cluster (≥3 broker'а) + ZooKeeper или KRaft
- Postgres HA (Patroni / streaming replication)
- MinIO distributed mode или AWS S3
