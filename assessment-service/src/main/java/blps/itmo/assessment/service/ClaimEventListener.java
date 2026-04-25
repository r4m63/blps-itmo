package blps.itmo.assessment.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import blps.itmo.platform.events.EventEnvelope;
import blps.itmo.platform.events.EventType;
import blps.itmo.platform.events.TopicNames;
import blps.itmo.platform.events.payload.AdditionalInfoProvidedPayload;
import blps.itmo.platform.events.payload.ClaimCreatedPayload;

@Component
public class ClaimEventListener {

    private final ObjectMapper objectMapper;
    private final AssessmentWorkflowService assessmentWorkflowService;

    public ClaimEventListener(ObjectMapper objectMapper, AssessmentWorkflowService assessmentWorkflowService) {
        this.objectMapper = objectMapper;
        this.assessmentWorkflowService = assessmentWorkflowService;
    }

    @KafkaListener(topics = TopicNames.CLAIM_EVENTS, groupId = "assessment-service")
    public void onClaimEvent(String rawEvent) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(rawEvent, EventEnvelope.class);
        if (envelope.getEventType() == EventType.CLAIM_CREATED) {
            assessmentWorkflowService.handleClaimCreated(
                    envelope,
                    objectMapper.treeToValue(envelope.getPayload(), ClaimCreatedPayload.class));
            return;
        }
        if (envelope.getEventType() == EventType.ADDITIONAL_INFO_PROVIDED) {
            assessmentWorkflowService.handleAdditionalInfo(
                    envelope,
                    objectMapper.treeToValue(envelope.getPayload(), AdditionalInfoProvidedPayload.class));
        }
    }
}
