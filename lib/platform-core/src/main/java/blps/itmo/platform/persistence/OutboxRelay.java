package blps.itmo.platform.persistence;

import java.time.Instant;
import java.util.EnumSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import blps.itmo.platform.events.EventEnvelope;

@Component
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String serviceName;

    public OutboxRelay(OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.service-name}") String serviceName) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }

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
                event.setStatus(OutboxStatus.FAILED);
                event.setRetryCount(event.getRetryCount() + 1);
                event.setErrorMessage(e.getMessage());
            }
            outboxEventRepository.save(event);
        }
    }
}
