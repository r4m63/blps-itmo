package blps.itmo.claim.kafka;

import blps.itmo.claim.kafka.config.TopicNames;
import blps.itmo.claim.kafka.payload.PenaltyApplicationFailedPayload;
import blps.itmo.claim.kafka.payload.PenaltyAppliedPayload;
import blps.itmo.claim.kafka.payload.PenaltyCountedPayload;
import blps.itmo.claim.kafka.payload.PenaltyRevokeFailedPayload;
import blps.itmo.claim.kafka.payload.PenaltyRevokedPayload;
import blps.itmo.claim.kafka.payload.UserDeactivatedPayload;
import blps.itmo.claim.kafka.processedmessage.ProcessedMessageService;
import blps.itmo.claim.saga.PenaltyApplicationSaga;
import blps.itmo.claim.service.ClaimService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ClaimEventConsumer {

    private final ObjectMapper objectMapper;
    private final ProcessedMessageService processedMessageService;
    private final ClaimService claimService;
    private final PenaltyApplicationSaga penaltyApplicationSaga;

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
        UUID sagaId = parseSagaId(envelope.sagaId());
        String eventType = envelope.eventType();
        if (EventType.PENALTY_APPLIED.equals(eventType)) {
            PenaltyAppliedPayload payload = objectMapper.convertValue(envelope.payload(), PenaltyAppliedPayload.class);
            if (sagaId != null) {
                penaltyApplicationSaga.onPenaltyApplied(sagaId, payload);
            } else {
                claimService.markPenaltyApplied(payload.claimId(), null);
            }
            processedMessageService.markProcessed(envelope.eventId());
        } else if (EventType.PENALTY_APPLICATION_FAILED.equals(eventType)) {
            PenaltyApplicationFailedPayload payload =
                    objectMapper.convertValue(envelope.payload(), PenaltyApplicationFailedPayload.class);
            if (sagaId != null) {
                penaltyApplicationSaga.onPenaltyApplicationFailed(sagaId, payload);
            } else {
                claimService.markPenaltyFailed(payload.claimId(), payload.reason());
            }
            processedMessageService.markProcessed(envelope.eventId());
        } else if (EventType.PENALTY_REVOKED.equals(eventType)) {
            PenaltyRevokedPayload payload = objectMapper.convertValue(envelope.payload(), PenaltyRevokedPayload.class);
            if (sagaId != null) {
                penaltyApplicationSaga.onPenaltyRevoked(sagaId, payload);
            }
            processedMessageService.markProcessed(envelope.eventId());
        } else if (EventType.PENALTY_REVOKE_FAILED.equals(eventType)) {
            PenaltyRevokeFailedPayload payload =
                    objectMapper.convertValue(envelope.payload(), PenaltyRevokeFailedPayload.class);
            if (sagaId != null) {
                penaltyApplicationSaga.onPenaltyRevokeFailed(sagaId, payload);
            }
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
        if (processedMessageService.alreadyProcessed(envelope.eventId())) {
            return;
        }
        UUID sagaId = parseSagaId(envelope.sagaId());
        String eventType = envelope.eventType();
        if (EventType.USER_DEACTIVATED.equals(eventType)) {
            UserDeactivatedPayload payload = objectMapper.convertValue(envelope.payload(), UserDeactivatedPayload.class);
            claimService.closeClaimsForDeactivatedUser(payload.userId(), payload.reason());
            processedMessageService.markProcessed(envelope.eventId());
        } else if (EventType.PENALTY_COUNTED.equals(eventType)) {
            PenaltyCountedPayload payload = objectMapper.convertValue(envelope.payload(), PenaltyCountedPayload.class);
            if (sagaId != null) {
                penaltyApplicationSaga.onPenaltyCounted(sagaId, payload);
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
