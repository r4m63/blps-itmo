package blps.itmo.claim.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import blps.itmo.platform.events.EventEnvelope;
import blps.itmo.platform.events.EventType;
import blps.itmo.platform.events.TopicNames;
import blps.itmo.platform.events.payload.AssessmentCompletedPayload;
import blps.itmo.platform.events.payload.PenaltyApplicationFailedPayload;
import blps.itmo.platform.events.payload.PenaltyAppliedPayload;
import blps.itmo.platform.events.payload.UserDeactivatedPayload;

@Component
public class ClaimEventListeners {

    private final ObjectMapper objectMapper;
    private final ClaimProcessService claimProcessService;

    public ClaimEventListeners(ObjectMapper objectMapper, ClaimProcessService claimProcessService) {
        this.objectMapper = objectMapper;
        this.claimProcessService = claimProcessService;
    }

    @KafkaListener(topics = TopicNames.ASSESSMENT_EVENTS, groupId = "claim-service")
    public void onAssessmentEvent(String rawEvent) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(rawEvent, EventEnvelope.class);
        if (envelope.getEventType() != EventType.ASSESSMENT_COMPLETED) {
            return;
        }
        claimProcessService.handleAssessmentCompleted(
                envelope,
                objectMapper.treeToValue(envelope.getPayload(), AssessmentCompletedPayload.class));
    }

    @KafkaListener(topics = TopicNames.PENALTY_EVENTS, groupId = "claim-service")
    public void onPenaltyEvent(String rawEvent) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(rawEvent, EventEnvelope.class);
        if (envelope.getEventType() == EventType.PENALTY_APPLIED) {
            claimProcessService.handlePenaltyApplied(
                    envelope,
                    objectMapper.treeToValue(envelope.getPayload(), PenaltyAppliedPayload.class));
            return;
        }
        if (envelope.getEventType() == EventType.PENALTY_APPLICATION_FAILED) {
            claimProcessService.handlePenaltyFailed(
                    envelope,
                    objectMapper.treeToValue(envelope.getPayload(), PenaltyApplicationFailedPayload.class));
        }
    }

    @KafkaListener(topics = TopicNames.AUTH_EVENTS, groupId = "claim-service")
    public void onAuthEvent(String rawEvent) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(rawEvent, EventEnvelope.class);
        if (envelope.getEventType() != EventType.USER_DEACTIVATED) {
            return;
        }
        claimProcessService.handleUserDeactivated(
                envelope,
                objectMapper.treeToValue(envelope.getPayload(), UserDeactivatedPayload.class));
    }
}
