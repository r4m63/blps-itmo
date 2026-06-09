# Runbook — Demo Scenarios

Пошаговая инструкция для демонстрации distributed transaction паттернов на защите лабораторной работы.

## Подготовка

### 1. Инфраструктура

```bash
cp .env.sample .env   # подправить порты если нужно
docker compose up -d  # zookeeper, kafka, kafka-ui, minio, 3× postgres
```

Если volumes уже существуют и хочется чистую БД:

```bash
docker compose down -v
docker compose up -d
```

### 2. Сервисы (каждый в своём терминале)

```bash
./gradlew :edge-service:bootRun
./gradlew :claim-service:bootRun
./gradlew :penalty-service:bootRun
```

Дождаться `Started ...Application in N seconds` в каждом терминале.

### 3. Smoke check

```bash
curl -s http://localhost:8080/actuator/health | jq .
curl -s http://localhost:8080/api/auth/users | jq .
```

### 4. Открыть для наблюдения

- Kafka UI: http://localhost:8088 — следить за событиями в `claim.events`, `penalty.events`, `identity.events`
- MinIO Console: http://localhost:9001 (user `minioadmin` / pass `minioadmin`)
- Postgres CLI готов: `docker compose exec postgres-claim psql -U blps -d blps_claim`

---

## Demo 1: Outbox dual-write resilience

**Цель:** показать, что claim сохраняется в БД даже когда Kafka недоступна, и событие допубликуется после восстановления.

### Шаги

1. Залогиниться как landlord:
   ```bash
   TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"userId":1}' | jq -r .token)
   ```

2. **Остановить Kafka:**
   ```bash
   docker compose stop kafka
   ```

3. Создать claim (БД доступна, Kafka — нет):
   ```bash
   curl -X POST http://localhost:8080/api/claims \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "tenantUserId": 3,
       "title": "Outbox demo",
       "description": "Should survive Kafka outage",
       "claimedAmount": 250,
       "currency": "USD"
     }'
   ```

   → возвращает 202 с `claimId` и `processingStatus: ASSESSMENT_IN_PROGRESS`.

4. Проверить, что событие лежит в outbox со `status=NEW` (или `FAILED` если relay уже пытался):
   ```bash
   docker compose exec postgres-claim psql -U blps -d blps_claim -c \
     "SELECT id, event_type, status, retry_count, error_message FROM outbox_events ORDER BY id DESC LIMIT 5;"
   ```

5. **Поднять Kafka обратно:**
   ```bash
   docker compose start kafka
   ```

6. Подождать ~5 секунд. Проверить, что outbox row перешёл в `PUBLISHED`:
   ```bash
   docker compose exec postgres-claim psql -U blps -d blps_claim -c \
     "SELECT id, event_type, status, published_at FROM outbox_events ORDER BY id DESC LIMIT 5;"
   ```

7. В Kafka UI на topic `claim.events` — увидеть появившееся `CLAIM_CREATED` событие.

**Что показано:** Transactional Outbox решает dual write problem. БД и Kafka не были в одной транзакции, но данные не потерялись.

---

## Demo 2: Eventual consistency

**Цель:** показать, что после `POST /api/claims` `GET` сначала показывает intermediate status, потом догоняется до финального.

### Шаги

1. Получить токен landlord'а (как в Demo 1).

2. Создать claim с маленькой суммой (закроется без штрафа):
   ```bash
   CLAIM=$(curl -s -X POST http://localhost:8080/api/claims \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"tenantUserId":3,"title":"EC demo","description":"...","claimedAmount":30,"currency":"USD"}')
   CLAIM_ID=$(echo "$CLAIM" | jq -r .claimId)
   echo "claim: $CLAIM_ID"
   ```

3. **Сразу же** опросить status:
   ```bash
   curl -s "http://localhost:8080/api/claims/$CLAIM_ID/process-status" -H "Authorization: Bearer $TOKEN" | jq .
   ```
   → `status: ASSESSMENT_IN_PROGRESS`, `terminal: false`.

4. Подождать 3-5 секунд (assessment worker poll: 2s) и опросить снова:
   ```bash
   curl -s "http://localhost:8080/api/claims/$CLAIM_ID/process-status" -H "Authorization: Bearer $TOKEN" | jq .
   ```
   → `status: CLOSED_NO_PENALTY`, `terminal: true`.

5. Посмотреть timeline:
   ```bash
   curl -s "http://localhost:8080/api/claims/$CLAIM_ID/timeline" -H "Authorization: Bearer $TOKEN" | jq .
   ```

**Что показано:** между HTTP 202 и финальным статусом проходит видимое время. Eventual consistency — это **проектное** свойство, отражённое в API через `processingStatus` + polling.

---

## Demo 3: Duplicate event handling

**Цель:** показать, что повторно доставленное событие не приводит к повторному side-effect.

### Шаги

1. Создать claim, дойти до `AWAITING_TENANT_RESPONSE`, отправить tenant response, support-decision с `applyPenalty=true` — дойти до `PENALTY_APPLIED`.

   Удобнее использовать `rest-client/scenarios/01-create-claim-async-penalty.http` целиком.

2. Найти `eventId` события `PENALTY_APPLIED`, которое уже было обработано:
   ```bash
   docker compose exec postgres-penalty psql -U blps -d blps_penalty -c \
     "SELECT id, payload_json->>'claimId' as claim_id, event_id FROM outbox_events WHERE event_type='PENALTY_APPLIED' ORDER BY id DESC LIMIT 1;"
   ```

3. Посмотреть в `claim-service` `processed_messages` — должна быть одна запись с этим `event_id`:
   ```bash
   docker compose exec postgres-claim psql -U blps -d blps_claim -c \
     "SELECT event_id, consumer_name, processed_at FROM processed_messages WHERE event_id = '<UUID>' ORDER BY processed_at DESC;"
   ```

4. Скопировать **то же самое сообщение** в Kafka вручную через Kafka UI или kafka-console-producer:
   - открыть Kafka UI → topic `penalty.events` → найти исходное сообщение
   - "Produce message" с тем же key (= `claimId`) и тем же body
   - **или** через CLI:
     ```bash
     docker compose exec kafka kafka-console-producer \
       --bootstrap-server kafka:9092 \
       --topic penalty.events \
       --property "parse.key=true" --property "key.separator=:"
     # Ввести: <claimId>:<полный исходный JSON envelope>
     ```

5. Подождать 2-3 секунды. Проверить:
   - `processed_messages` всё ещё содержит **одну** запись для этого `event_id` в consumer'е claim-service
   - `claim.status` всё ещё `PENALTY_APPLIED` (без второго перехода)
   - `users.penalty_count` пользователя tenant НЕ удвоился

   ```bash
   docker compose exec postgres-claim psql -U blps -d blps_claim -c \
     "SELECT count(*) FROM processed_messages WHERE event_id = '<UUID>';"
   # → 2 (один на edge-service consumer, один на claim-service consumer; для каждого consumer'а dedup независим)

   docker compose exec postgres-identity psql -U blps -d blps_identity -c \
     "SELECT id, email, penalty_count FROM users WHERE id = <tenantId>;"
   # → penalty_count не изменился по сравнению с моментом до повторной публикации
   ```

**Что показано:** Inbox-паттерн (`processed_messages`) гарантирует идемпотентность consumer'а даже при at-least-once доставке Kafka.

---

## Demo 4: Penalty failure + manual recovery

**Цель:** показать saga с non-rollbackable шагом и manual recovery.

### Шаги

1. Создать claim, дойти до `SUPPORT_REVIEW` (через `tenant-response`).

2. Отправить support-decision с `simulateFailure=true`:
   ```bash
   curl -X POST "http://localhost:8080/api/claims/$CLAIM_ID/support-decision" \
     -H "Authorization: Bearer $SUPPORT_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "applyPenalty": true,
       "penaltyAmount": 175,
       "penaltyCurrency": "USD",
       "note": "Failure demo",
       "simulateFailure": true
     }'
   ```

3. Подождать 3-5 секунд. Проверить status:
   ```bash
   curl -s "http://localhost:8080/api/claims/$CLAIM_ID/process-status" -H "Authorization: Bearer $TOKEN" | jq .
   ```
   → `status: PENALTY_PROCESSING_FAILED`.

4. Найти `operationId`:
   ```bash
   curl -s "http://localhost:8080/api/penalties/claims/$CLAIM_ID" -H "Authorization: Bearer $ADMIN_TOKEN" | jq .
   ```
   → массив с одной operation, `status: FAILED`, есть `operationId`.

5. Retry operation:
   ```bash
   curl -X POST "http://localhost:8080/api/penalties/operations/<OPERATION_ID>/retry" \
     -H "Authorization: Bearer $ADMIN_TOKEN"
   ```

6. Подождать 3-5 секунд. Проверить:
   ```bash
   curl -s "http://localhost:8080/api/claims/$CLAIM_ID/process-status" -H "Authorization: Bearer $TOKEN" | jq .
   ```
   → `status: PENALTY_APPLIED`.

7. Посмотреть timeline:
   ```bash
   curl -s "http://localhost:8080/api/claims/$CLAIM_ID/timeline" -H "Authorization: Bearer $TOKEN" | jq .
   ```

**Что показано:** saga с компенсацией-как-бизнес-действием. Failure не откатывается обычным rollback, а вызывает явный intermediate state + manual repair endpoint.

---

## Demo 5: User deactivation distributed reaction

**Цель:** показать, что бизнес-факт в одном сервисе вызывает корректное состояние в другом без общей транзакции.

### Шаги

1. Залогиниться как landlord (id=1), создать 2-3 открытых claim'а. Не дожидаться их закрытия.

2. Залогиниться как admin (id=6):
   ```bash
   ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"userId":6}' | jq -r .token)
   ```

3. Деактивировать landlord'а:
   ```bash
   curl -X POST http://localhost:8080/api/auth/users/1/deactivate \
     -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"reason":"Demo: distributed reaction"}'
   ```

4. **Сразу** проверить open claims:
   ```bash
   docker compose exec postgres-claim psql -U blps -d blps_claim -c \
     "SELECT id, status FROM claims WHERE landlord_id = 1 AND closed_at IS NULL;"
   ```
   → ещё открыты (eventual consistency window).

5. Подождать 3-5 секунд. Проверить снова:
   ```bash
   docker compose exec postgres-claim psql -U blps -d blps_claim -c \
     "SELECT id, status, resolution_note FROM claims WHERE landlord_id = 1 ORDER BY id DESC LIMIT 5;"
   ```
   → все статусы `CLOSED_NO_PENALTY` с note про deactivation.

6. Посмотреть Kafka UI: в `identity.events` появилось `USER_DEACTIVATED`; в `claim.events` — несколько `CLAIM_CLOSED_NO_PENALTY` (по одному на каждый закрытый claim).

**Что показано:** distributed reaction через Saga choreography. `edge-service` и `claim-service` не делили транзакцию, но система пришла в согласованное состояние.

---

## Demo 6: MinIO outside XA

**Цель:** показать saga для external resource, который не может быть частью XA.

### Шаги

1. Залогиниться как landlord, получить токен.

2. Init attachment:
   ```bash
   INIT=$(curl -s -X POST http://localhost:8080/api/attachments/init \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"originalFilename":"damage.jpg","contentType":"image/jpeg"}')
   ATTACH_ID=$(echo "$INIT" | jq -r .attachmentId)
   UPLOAD_URL=$(echo "$INIT" | jq -r .uploadUrl)
   echo "attachment: $ATTACH_ID, url: $UPLOAD_URL"
   ```

3. Проверить metadata row создан со `status=INITIALIZED`:
   ```bash
   docker compose exec postgres-claim psql -U blps -d blps_claim -c \
     "SELECT id, status, claim_id FROM attachments WHERE id = $ATTACH_ID;"
   ```

4. Загрузить файл напрямую в MinIO по presigned URL:
   ```bash
   echo "fake jpg content" | curl -X PUT --upload-file - "$UPLOAD_URL"
   ```

5. Confirm (claim-service делает HEAD-check в MinIO):
   ```bash
   curl -X POST "http://localhost:8080/api/attachments/$ATTACH_ID/confirm" \
     -H "Authorization: Bearer $TOKEN"
   ```
   → `status: CONFIRMED`.

6. Создать claim с этим attachment:
   ```bash
   curl -X POST http://localhost:8080/api/claims \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d "{\"tenantUserId\":3,\"title\":\"Saga demo\",\"description\":\"...\",\"claimedAmount\":100,\"currency\":\"USD\",\"attachmentIds\":[$ATTACH_ID]}"
   ```

7. Проверить, что attachment стал `BOUND`:
   ```bash
   docker compose exec postgres-claim psql -U blps -d blps_claim -c \
     "SELECT id, status, claim_id, bound_at FROM attachments WHERE id = $ATTACH_ID;"
   ```

### Опционально: orphan reconciliation

8. Init ещё один attachment, **не** делать confirm, **не** привязывать к claim.

9. Дождаться через час (или временно поменять `@Scheduled` на `fixedDelay=10000` для demo) — attachment перейдёт в `ORPHANED` и объект удалится из MinIO.

**Что показано:** MinIO не участвует в БД-транзакции (это external resource). Saga `INIT → CONFIRM → BIND` обеспечивает консистентность через явные intermediate state. Reconciliation job ловит orphan'ы.

---

## Чек-лист защиты

Для зачёта лабы должны работать:

- [ ] **Demo 1** — Outbox dual-write resilience
- [ ] **Demo 2** — Eventual consistency (intermediate state visible через polling)
- [ ] **Demo 3** — Duplicate event handling (Inbox/processed_messages)
- [ ] **Demo 4** — Penalty failure + manual recovery
- [ ] **Demo 5** — User deactivation distributed reaction
- [ ] **Demo 6** — MinIO outside XA (init → upload → confirm → bind)

Если хотя бы один сценарий ломается — это **BLOCKER** для текущей версии системы.

---

## Troubleshooting

### Kafka недоступна / consumer lag растёт

- Проверить `docker compose ps` — kafka должна быть `running (healthy)`
- Kafka UI → consumer groups → lag
- Логи сервиса — ищем `org.apache.kafka.common.errors.TimeoutException`

### Outbox events застряли в `NEW` / `FAILED`

```bash
docker compose exec postgres-claim psql -U blps -d blps_claim -c \
  "SELECT id, event_type, status, retry_count, error_message, created_at FROM outbox_events
   WHERE status IN ('NEW', 'FAILED') ORDER BY id;"
```

- Если `retry_count >= 5` — проблема в payload или контракте, нужен ручной разбор
- Если `retry_count=0` старше 30s — relay сломался / Kafka недоступна

### Claim застрял в intermediate state

- `GET /api/claims/{id}/timeline` — посмотреть последний переход
- Если `PENALTY_PROCESSING` дольше 5 минут — penalty-service worker не подхватил event, проверить `processed_messages` в `blps_penalty`
- В крайнем случае: `POST /api/claims/{id}/repair/close` для принудительного закрытия

### Тест-сценарий `.http` падает

- Проверить, что все 3 сервиса запущены: `curl http://localhost:8080/actuator/health`
- Проверить, что Postgres / Kafka / MinIO в `docker compose ps`
- Очистить state: `docker compose down -v && docker compose up -d && ./gradlew bootRun ...`
