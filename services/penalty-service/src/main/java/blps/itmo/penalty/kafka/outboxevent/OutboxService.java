package blps.itmo.penalty.kafka.outboxevent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void enqueue(String aggregateType, String aggregateId, String eventType, String topic, Object payload) {
        enqueue(aggregateType, aggregateId, eventType, topic, payload, null, null);
    }

    @Transactional
    public void enqueue(String aggregateType, String aggregateId, String eventType, String topic, Object payload,
                        UUID sagaId) {
        enqueue(aggregateType, aggregateId, eventType, topic, payload, sagaId, null);
    }

    @Transactional
    public void enqueue(String aggregateType, String aggregateId, String eventType, String topic, Object payload,
                        UUID sagaId, UUID correlationId) {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setTopic(topic);
        event.setPayload(toJson(payload));
        event.setSagaId(sagaId);
        event.setCorrelationId(correlationId);
        repository.save(event);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize outbox payload", e);
        }
    }
}
