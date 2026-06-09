package blps.itmo.penalty.kafka;

import blps.itmo.penalty.kafka.config.TopicNames;
import blps.itmo.penalty.kafka.payload.PenaltyApplicationRequestedPayload;
import blps.itmo.penalty.kafka.payload.PenaltyRevokeCommandPayload;
import blps.itmo.penalty.kafka.processedmessage.ProcessedMessageService;
import blps.itmo.penalty.service.PenaltyService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PenaltyEventConsumer {

    private final ObjectMapper objectMapper;
    private final ProcessedMessageService processedMessageService;
    private final PenaltyService penaltyService;

    @KafkaListener(topics = TopicNames.PENALTY_EVENTS, groupId = "penalty-service")
    @Transactional
    public void onPenaltyEvent(String message) throws Exception {
        EventEnvelope<Object> envelope = objectMapper.readValue(
                message,
                new TypeReference<EventEnvelope<Object>>() {
                }
        );
        if (processedMessageService.alreadyProcessed(envelope.eventId())) {
            return;
        }
        UUID sagaId = parseSagaId(envelope.sagaId());
        String eventType = envelope.eventType();
        if (EventType.PENALTY_APPLICATION_REQUESTED.equals(eventType)) {
            PenaltyApplicationRequestedPayload payload =
                    objectMapper.convertValue(envelope.payload(), PenaltyApplicationRequestedPayload.class);
            penaltyService.handlePenaltyRequested(payload, sagaId);
            processedMessageService.markProcessed(envelope.eventId());
        } else if (EventType.PENALTY_REVOKE_COMMAND.equals(eventType)) {
            PenaltyRevokeCommandPayload payload =
                    objectMapper.convertValue(envelope.payload(), PenaltyRevokeCommandPayload.class);
            penaltyService.revoke(payload, sagaId);
            processedMessageService.markProcessed(envelope.eventId());
        }
    }

    private static UUID parseSagaId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
