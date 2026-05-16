package blps.itmo.claim.kafka;

import blps.itmo.claim.kafka.outboxevent.OutboxEvent;
import blps.itmo.claim.kafka.outboxevent.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${outbox.relay-delay-ms:1000}")
    @Transactional
    public void publishBatch() {
        List<OutboxEvent> batch = repository.findBatchForPublish(PageRequest.of(0, 50));
        for (OutboxEvent event : batch) {
            try {
                EventEnvelope<Object> envelope = new EventEnvelope<>(
                        event.getId(),
                        event.getEventType(),
                        event.getSagaId() == null ? null : event.getSagaId().toString(),
                        event.getCorrelationId() == null ? null : event.getCorrelationId().toString(),
                        Instant.now(),
                        objectMapper.readValue(event.getPayload(), Object.class)
                );
                String json = objectMapper.writeValueAsString(envelope);
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), json);
                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
            } catch (Exception ex) {
                event.setRetryCount(event.getRetryCount() + 1);
                event.setLastError(ex.getMessage());
            }
        }
    }
}
