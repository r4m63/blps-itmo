package blps.itmo.auth.kafka;

import blps.itmo.auth.domain.User;
import blps.itmo.auth.kafka.config.TopicNames;
import blps.itmo.auth.kafka.outboxevent.OutboxService;
import blps.itmo.auth.kafka.payload.PenaltyAppliedPayload;
import blps.itmo.auth.kafka.payload.PenaltyCountedPayload;
import blps.itmo.auth.kafka.payload.PenaltyRevokedPayload;
import blps.itmo.auth.kafka.processedmessage.ProcessedMessageService;
import blps.itmo.auth.service.UserService;
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
    private final UserService userService;
    private final OutboxService outboxService;

    @KafkaListener(topics = TopicNames.PENALTY_EVENTS, groupId = "edge-service")
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
        if (EventType.PENALTY_APPLIED.equals(eventType)) {
            PenaltyAppliedPayload payload = objectMapper.convertValue(envelope.payload(), PenaltyAppliedPayload.class);
            User updated = userService.incrementPenaltyCount(payload.tenantId());
            outboxService.enqueue(
                    "user",
                    updated.getId().toString(),
                    EventType.PENALTY_COUNTED,
                    TopicNames.IDENTITY_EVENTS,
                    new PenaltyCountedPayload(payload.claimId(), payload.tenantId(),
                            payload.operationId(), updated.getPenaltyCount()),
                    sagaId
            );
            processedMessageService.markProcessed(envelope.eventId());
        } else if (EventType.PENALTY_REVOKED.equals(eventType)) {
            PenaltyRevokedPayload payload = objectMapper.convertValue(envelope.payload(), PenaltyRevokedPayload.class);
            if (payload.wasApplied() && payload.tenantId() != null) {
                userService.decrementPenaltyCount(payload.tenantId());
            }
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
