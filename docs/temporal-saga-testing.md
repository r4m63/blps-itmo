# Temporal Saga Testing Runbook

> Superseded by `docs/camunda-lab4-runbook.md` after the Camunda migration.
> The current `claim-service` no longer starts Temporal workflows; saga state is
> stored in Camunda process variables and can be inspected in Camunda Cockpit.

## 1. Что запускать

Инфраструктура:

```bash
docker compose up -d kafka kafka-ui minio postgres-auth postgres-claim postgres-penalty postgres-temporal temporal temporal-ui jira
```

Проверка:

```bash
docker compose ps postgres-temporal temporal temporal-ui
docker compose config
```

Сервисы в трех отдельных терминалах:

```bash
./gradlew :auth-service:bootRun
./gradlew :claim-service:bootRun
./gradlew :penalty-service:bootRun
```

Порты:

| Что | URL |
|---|---|
| API gateway/auth-service | `http://localhost:8080` |
| claim-service | `http://localhost:8081` |
| penalty-service | `http://localhost:8084` |
| Temporal gRPC | `localhost:7233` |
| Temporal UI | `http://localhost:8086` |
| Kafka UI | `http://localhost:8085` |

В Temporal UI выбирай namespace `default`.

## 2. Быстрые timeout настройки

Для ручных тестов компенсации удобно временно уменьшить таймеры в `services/claim-service/src/main/resources/application.yml`:

```yaml
temporal:
  timeout-seconds:
    awaiting-penalty-applied: 10
    awaiting-penalty-counted: 10
    compensating-retry: 5
```

После изменения перезапусти только `claim-service`.

## 3. Где лежат готовые запросы

Полностью автоматизированный сценарий:

```bash
./scripts/temporal-saga-all-states.sh
```

Он сам поднимает инфраструктуру через Docker Compose, запускает `auth-service`, `claim-service`, `penalty-service`, временно останавливает нужные сервисы и проверяет все состояния через `/api/sagas/{id}`. Перед запуском останови ручные `bootRun` процессы на портах `8080`, `8081`, `8084`, иначе скрипт остановится с ошибкой.

Основной файл для Temporal:

```text
rest-client/scenarios/36-temporal-saga-states.http
```

Расширенный файл, который явно покрывает все `SagaState` и пишет, какие состояния покрывает каждый блок:

```text
rest-client/scenarios/37-temporal-saga-all-statuses-direct.http
```

Он ходит напрямую в `claim-service` на `8081` через `X-User-Id`, поэтому подходит для сценариев, где нужно временно остановить `auth-service`.

Дополнительно можно использовать старые сценарии:

| Файл | Что проверяет |
|---|---|
| `35-lab3-saga-compensation.http` | happy path и инспекция saga |
| `31-lab3-penalty-failure-recovery.http` | `simulateFailure=true`, состояние `PENALTY_FAILED` |
| `34-lab3-penalty-threshold-cascade.http` | несколько независимых saga подряд |

## 4. Какие состояния покрывать

| Состояние | Как получить | Что ожидать |
|---|---|---|
| `AWAITING_PENALTY_APPLIED` | Остановить `penalty-service`, затем отправить `support-decision` | Workflow запущен, ждет `PENALTY_APPLIED` или `PENALTY_APPLICATION_FAILED` |
| `AWAITING_PENALTY_COUNTED` | Остановить `auth-service`, оставить `penalty-service` включенным, затем отправить `support-decision` | Penalty применен, workflow ждет `PENALTY_COUNTED` |
| `COMPLETED` | Все сервисы включены, `simulateFailure=false` | Пришли `penaltyApplied` и `penaltyCounted`, claim стал `PENALTY_APPLIED` |
| `PENALTY_FAILED` | Все сервисы включены, `simulateFailure=true` | Пришел `penaltyApplicationFailed`, claim стал `PENALTY_PROCESSING_FAILED` |
| `COMPENSATING_REVOKE` | Остановить `auth-service`, дождаться timeout | Workflow запустил compensation и отправил `PENALTY_REVOKE_COMMAND` |
| `COMPENSATED` | `auth-service` остановлен, `penalty-service` включен, дождаться timeout + revoke | Пришел `penaltyRevoked`, claim стал `PENALTY_PROCESSING_FAILED` |
| `COMPENSATION_FAILED` | Остановить `penalty-service`, дождаться timeout и всех retry compensation | `PENALTY_REVOKED` не пришел после `maxAttempts` |

`STARTED` обычно руками не ловится: workflow почти сразу переходит в `AWAITING_PENALTY_APPLIED`.

## 5. Как смотреть результат

Через API:

```http
GET http://localhost:8080/api/sagas/{{sagaId}}
Authorization: Basic admin@blps.local adminpass
```

Ключевое поле:

```json
{
  "state": "COMPLETED"
}
```

Через Temporal UI:

1. Открой `http://localhost:8086`.
2. Namespace: `default`.
3. Найди workflow:

```text
penalty-application-{sagaId}
```

4. Открой `History`.

Что искать:

| Сценарий | Temporal History |
|---|---|
| Happy path | `penaltyApplied`, `penaltyCounted`, `markPenaltyCounted`, `WorkflowExecutionCompleted` |
| Failure | `penaltyApplicationFailed`, `markPenaltyApplicationFailed`, `WorkflowExecutionCompleted` |
| Timeout compensation | `TimerFired`, `enqueuePenaltyRevoke`, `penaltyRevoked`, `markPenaltyRevoked`, `WorkflowExecutionCompleted` |
| Compensation failed | несколько `TimerFired`/`enqueuePenaltyRevoke`, затем workflow state query показывает `COMPENSATION_FAILED` |

## 6. Порядок прогона

Рекомендуемый порядок:

1. `COMPLETED`: все сервисы включены, прогнать happy path.
2. `PENALTY_FAILED`: все сервисы включены, прогнать `simulateFailure=true`.
3. `AWAITING_PENALTY_APPLIED`: остановить `penalty-service`, прогнать до `support-decision`, сразу проверить saga.
4. `AWAITING_PENALTY_COUNTED`: включить `penalty-service`, остановить `auth-service`, прогнать до `support-decision`, сразу проверить saga.
5. `COMPENSATED`: при остановленном `auth-service` дождаться timeout, `penalty-service` должен быть включен.
6. `COMPENSATION_FAILED`: остановить `penalty-service`, дождаться timeout + `maxAttempts * compensatingRetrySeconds`.

Важно: если сервис остановлен как Gradle process, просто прерви его `Ctrl+C`. Инфраструктурные контейнеры (`temporal`, `kafka`, `postgres-*`) при этом не останавливай.
