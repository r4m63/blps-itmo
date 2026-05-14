package blps.itmo.auth.messaging;

import blps.itmo.auth.messaging.payload.PenaltyAppliedPayload;
import blps.itmo.auth.service.UserService;
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
    private final UserService userService;

    @KafkaListener(topics = TopicNames.PENALTY_EVENTS, groupId = "edge-service")
    @Transactional
    public void onPenaltyEvent(String message) throws Exception {
        EventEnvelope<PenaltyAppliedPayload> envelope = objectMapper.readValue(
                message,
                new TypeReference<EventEnvelope<PenaltyAppliedPayload>>() {
                }
        );
        if (!EventType.PENALTY_APPLIED.equals(envelope.eventType())) {
            return;
        }
        if (processedMessageService.alreadyProcessed(envelope.eventId())) {
            return;
        }
        PenaltyAppliedPayload payload = envelope.payload();
        userService.incrementPenaltyCount(payload.tenantId());
        processedMessageService.markProcessed(envelope.eventId());
    }
}
