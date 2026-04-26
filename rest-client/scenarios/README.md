# Lab3 Scenarios

В репозитории оставлены только актуальные `.http` сценарии для текущей event-driven реализации.
Все внешние запросы идут через `api-gateway` на `http://localhost:8080`; сначала выполняется
`POST /api/auth/login`, затем JWT передаётся как `Authorization: Bearer ...`.

- [30-lab3-async-penalty.http](/Users/ramil/Projects/blps-itmo/rest-client/scenarios/30-lab3-async-penalty.http)
  Основной happy path:
  `claim-service -> assessment-service -> additional info -> tenant response -> support decision -> penalty-service -> auth-service`.

- [31-lab3-penalty-failure-recovery.http](/Users/ramil/Projects/blps-itmo/rest-client/scenarios/31-lab3-penalty-failure-recovery.http)
  Failure path:
  `PENALTY_PROCESSING -> PENALTY_PROCESSING_FAILED -> manual retry -> PENALTY_APPLIED`.

- [32-lab3-user-deactivation.http](/Users/ramil/Projects/blps-itmo/rest-client/scenarios/32-lab3-user-deactivation.http)
  Distributed reaction:
  `auth-service` деактивирует пользователя, `claim-service` асинхронно закрывает его открытые заявки.

- [33-lab3-attachment-saga.http](/Users/ramil/Projects/blps-itmo/rest-client/scenarios/33-lab3-attachment-saga.http)
  Attachment saga:
  `storage-service` создаёт/подтверждает attachment metadata, а `claim-service` запрашивает binding через Kafka.

Что показывать на защите:

- outbox после локального коммита
- eventual consistency между `claim-service` и `assessment-service`
- идемпотентную обработку повторной доставки
- ручное восстановление после `PENALTY_APPLICATION_FAILED`
- MinIO/attachment flow без XA через `init -> confirm -> bind`
- audit trail через `audit-service`
