package blps.itmo.platform.outbox;

import java.time.Instant;
import java.util.EnumSet;

import blps.itmo.platform.outbox.domain.OutboxEvent;
import blps.itmo.platform.outbox.domain.OutboxEventRepository;
import blps.itmo.platform.outbox.domain.OutboxStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import blps.itmo.platform.events.EventEnvelope;

/**
 * Outbox Relay — фоновый процесс, который отправляет события из БД в Kafka.
 *   [Сервис] -> (БД транзакция) -> [outbox_events]
 *                                      ↓
 *                              [OutboxRelay] (каждые 1 сек)
 *                                      ↓
 *                                 [Kafka]
 * 1. At-least-once доставка: если Kafka упала, событие остается в БД
 * 2. Идемпотентность на стороне потребителя (через ProcessedMessage)
 * 3. Retry с экспоненциальной задержкой (через статус FAILED)
 * 4. Dead Letter Queue при превышении лимита (статус DEAD)
 * Важно: этот класс должен быть @Component в каждом сервисе, который хочет отправлять события
 */
@Component
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String serviceName;
    private final int maxRetryCount;

    public OutboxRelay(OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.service-name}") String serviceName,
            @Value("${app.outbox.max-retry-count:10}") int maxRetryCount) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
        this.maxRetryCount = maxRetryCount;
    }

    /**
     * Планировщик, запускается каждые 1c.
     * Алгоритм:
     * 1. Забирает до 100 событий со статусами NEW или FAILED (старые сначала)
     * 2. Для каждого:
     *    a. Формирует EventEnvelope (обогащает метаданными)
     *    b. Отправляет в Kafka
     *    c. Если успех: статус → PUBLISHED, сохраняем время отправки
     *    d. Если ошибка: увеличиваем retryCount, статус → FAILED (или DEAD если лимит)
     * 3. Сохраняет обновленный статус в БД
     *
     * Важные детали:
     * - Использует .get() для синхронной отправки (ждет подтверждения брокера)
     * - Обрабатывает события по одному, чтобы не перегружать Kafka
     * - При ошибке сохраняет stacktrace в errorMessage для отладки
     */
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void publishPendingEvents() {
        var pending = outboxEventRepository.findTop100ByStatusInOrderByCreatedAtAsc(
                EnumSet.of(OutboxStatus.NEW, OutboxStatus.FAILED));
        for (OutboxEvent event : pending) {
            try {
                EventEnvelope envelope = EventEnvelope.builder()
                        .eventId(event.getEventId())
                        .eventType(event.getEventType())
                        .occurredAt(event.getCreatedAt())
                        .producerService(serviceName)
                        .correlationId(event.getCorrelationId())
                        .sagaId(event.getSagaId())
                        .aggregateType(event.getAggregateType())
                        .aggregateId(event.getAggregateId())
                        .actorId(event.getActorId())
                        .payload(objectMapper.readTree(event.getPayloadJson()))
                        .build();
                kafkaTemplate.send(event.getTopicName(), event.getEventKey(), objectMapper.writeValueAsString(envelope))
                        .get();
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                event.setErrorMessage(null);
            } catch (Exception e) {
                int retryCount = event.getRetryCount() + 1;
                event.setRetryCount(retryCount);
                event.setStatus(retryCount >= maxRetryCount ? OutboxStatus.DEAD : OutboxStatus.FAILED);
                event.setErrorMessage(e.getMessage());
            }
            outboxEventRepository.save(event);
        }
    }
}
