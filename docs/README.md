# BLPS ITMO Lab3 — System Design Documentation

Документация целевой архитектуры. **Source of truth** — код, документация догоняет код. Если файл противоречит коду — обнови документ, не игнорируй.

## Структура документации

| Файл | Назначение |
|---|---|
| [architecture.md](architecture.md) | High-level System Design: 3 сервиса, bounded contexts, коммуникация |
| [business-process.md](business-process.md) | Бизнес-процесс из BPMN, claim lifecycle, акторы, статусы, переходы |
| [distributed-transactions.md](distributed-transactions.md) | Outbox / Inbox / Saga / Idempotency — паттерны и где они применяются |
| [event-catalog.md](event-catalog.md) | Каталог Kafka событий: топики, payload'ы, producer/consumer matrix |
| [api-reference.md](api-reference.md) | Внешний HTTP API + внутренние gRPC контракты |
| [data-model.md](data-model.md) | DDL и data ownership по сервисам |
| [deployment-and-operations.md](deployment-and-operations.md) | Запуск, порты, env vars, инфраструктура |
| [runbook.md](runbook.md) | Demo-сценарии для защиты лабы |
| [bpmn/blps1.bpmn](bpmn/blps1.bpmn) | BPMN как **reference model** бизнес-процесса (не исполняется engine'ом) |

## Целевая архитектура (TL;DR)

3 микросервиса + Kafka + 3× Postgres + MinIO:

- **`edge-service`** — единая точка входа (HTTP + JWT) + identity (users, roles, penalty count)
- **`claim-service`** — claim aggregate, lifecycle state machine, attachments (MinIO saga), assessment, notifications, timeline
- **`penalty-service`** — async применение штрафа, retry, manual recovery

Sync межсервисное взаимодействие — **gRPC**. Async — **Kafka** через **Transactional Outbox**.

## С чего читать

1. [architecture.md](architecture.md) — какие сервисы и почему именно они
2. [business-process.md](business-process.md) — что эта система делает с точки зрения бизнеса
3. [distributed-transactions.md](distributed-transactions.md) — как достигается eventual consistency без XA
4. [runbook.md](runbook.md) — как запустить и что показать
