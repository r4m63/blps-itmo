package blps.itmo.claim.messaging;

import blps.itmo.claim.messaging.payload.PenaltyApplicationFailedPayload;
import blps.itmo.claim.messaging.payload.PenaltyAppliedPayload;
import blps.itmo.claim.messaging.payload.UserDeactivatedPayload;
import blps.itmo.claim.service.ClaimService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ClaimEventConsumer {

    private final ObjectMapper objectMapper;
    private final ProcessedMessageService processedMessageService;
    private final ClaimService claimService;

    @KafkaListener(topics = TopicNames.PENALTY_EVENTS, groupId = "claim-service")
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
        if (EventType.PENALTY_APPLIED.equals(envelope.eventType())) {
            PenaltyAppliedPayload payload = objectMapper.convertValue(envelope.payload(), PenaltyAppliedPayload.class);
            claimService.markPenaltyApplied(payload.claimId(), null);
            processedMessageService.markProcessed(envelope.eventId());
        } else if (EventType.PENALTY_APPLICATION_FAILED.equals(envelope.eventType())) {
            PenaltyApplicationFailedPayload payload = objectMapper.convertValue(envelope.payload(), PenaltyApplicationFailedPayload.class);
            claimService.markPenaltyFailed(payload.claimId(), payload.reason());
            processedMessageService.markProcessed(envelope.eventId());
        }
    }

    @KafkaListener(topics = TopicNames.IDENTITY_EVENTS, groupId = "claim-service")
    @Transactional
    public void onIdentityEvent(String message) throws Exception {
        EventEnvelope<Object> envelope = objectMapper.readValue(
                message,
                new TypeReference<EventEnvelope<Object>>() {
                }
        );
        if (!EventType.USER_DEACTIVATED.equals(envelope.eventType())) {
            return;
        }
        if (processedMessageService.alreadyProcessed(envelope.eventId())) {
            return;
        }
        UserDeactivatedPayload payload = objectMapper.convertValue(envelope.payload(), UserDeactivatedPayload.class);
        claimService.closeClaimsForDeactivatedUser(payload.userId(), payload.reason());
        processedMessageService.markProcessed(envelope.eventId());
    }
}
