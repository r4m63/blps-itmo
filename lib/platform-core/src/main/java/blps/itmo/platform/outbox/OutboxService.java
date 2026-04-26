package blps.itmo.platform.outbox;

import java.time.Instant;
import java.util.UUID;

import blps.itmo.platform.outbox.domain.OutboxEvent;
import blps.itmo.platform.outbox.domain.OutboxEventRepository;
import blps.itmo.platform.outbox.domain.OutboxStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import blps.itmo.platform.events.EventType;
import blps.itmo.platform.events.TopicNames;

@Service
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Используется в любом методе сервиса в бд транзакции
     * Записывает событие в outbox для последующей отправки в Kafka.
     */
    public void record(EventType eventType,
            String aggregateType,
            Object aggregateId,
            String correlationId,
            String sagaId,
            Long actorId,
            Object payload) {
        try {
            outboxEventRepository.save(OutboxEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .topicName(TopicNames.resolve(eventType))
                    .eventKey(String.valueOf(aggregateId))
                    .aggregateType(aggregateType)
                    .aggregateId(String.valueOf(aggregateId))
                    .correlationId(correlationId)
                    .sagaId(sagaId)
                    .actorId(actorId)
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .status(OutboxStatus.NEW)
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
