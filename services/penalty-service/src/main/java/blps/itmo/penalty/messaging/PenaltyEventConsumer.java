package blps.itmo.penalty.messaging;

import blps.itmo.penalty.messaging.payload.PenaltyApplicationRequestedPayload;
import blps.itmo.penalty.service.PenaltyService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PenaltyEventConsumer {

    private final ObjectMapper objectMapper;
    private final ProcessedMessageService processedMessageService;
    private final PenaltyService penaltyService;

    @KafkaListener(topics = TopicNames.PENALTY_EVENTS, groupId = "penalty-service")
    @Transactional
    public void onPenaltyEvent(String message) throws Exception {
        EventEnvelope<PenaltyApplicationRequestedPayload> envelope = objectMapper.readValue(
                message,
                new TypeReference<EventEnvelope<PenaltyApplicationRequestedPayload>>() {
                }
        );
        if (!EventType.PENALTY_APPLICATION_REQUESTED.equals(envelope.eventType())) {
            return;
        }
        if (processedMessageService.alreadyProcessed(envelope.eventId())) {
            return;
        }
        penaltyService.handlePenaltyRequested(envelope.payload());
        processedMessageService.markProcessed(envelope.eventId());
    }
}
