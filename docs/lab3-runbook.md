# Lab3 Runbook

## Infra

1. Copy `.env.sample` to `.env` and adjust ports if needed.
2. Start infra:

```bash
docker compose up -d
```

Если Postgres volume уже был создан раньше, `sql/init_*_service.sql` повторно не выполнятся. Для полной переинициализации схем используй:

```bash
docker compose down -v
docker compose up -d
```

This starts:

- `zookeeper`
- `kafka`
- `kafka-ui`
- `minio`
- `postgres-auth`
- `postgres-claim`
- `postgres-assessment`
- `postgres-penalty`
- `postgres-notification`
- `postgres-audit`

## Services

Run each service in a separate shell:

```bash
./gradlew :auth-service:bootRun
./gradlew :claim-service:bootRun
./gradlew :assessment-service:bootRun
./gradlew :penalty-service:bootRun
./gradlew :notification-service:bootRun
./gradlew :audit-service:bootRun
```

Default ports:

- `claim-service`: `8081`
- `auth-service`: `8082`
- `assessment-service`: `8083`
- `penalty-service`: `8084`
- `notification-service`: `8085`
- `audit-service`: `8086`
- `zookeeper`: `${ZOOKEEPER_PORT}` by default `2181`
- `kafka host port`: `${KAFKA_PORT}` by default `9092`
- `kafka external port`: `${KAFKA_EXTERNAL_PORT}` by default `29092`
- `kafka-ui`: `${KAFKA_UI_PORT}` by default `8088`
- `minio api`: `${MINIO_PORT}` by default `9000`
- `minio console`: `${MINIO_CONSOLE_PORT}` by default `9001`

## Demo flows

Use these HTTP scenarios:

- `rest-client/scenarios/30-lab3-async-penalty.http`
- `rest-client/scenarios/31-lab3-penalty-failure-recovery.http`
- `rest-client/scenarios/32-lab3-user-deactivation.http`

## What to show during the demo

1. `ClaimCreated` is first stored locally and only then published through the outbox relay.
2. `claim-service` and `assessment-service` are temporarily inconsistent while async assessment is running.
3. `PenaltyApplicationRequested` moves claim into `PENALTY_PROCESSING`, not directly into final success.
4. Duplicate delivery is tolerated by `processed_messages`.
5. Manual recovery is possible through `POST /api/penalties/operations/{operationId}/retry`.
6. `audit-service` shows the end-to-end event trail by `claimId`.
