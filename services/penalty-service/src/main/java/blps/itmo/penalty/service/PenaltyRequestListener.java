package blps.itmo.penalty.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import blps.itmo.platform.events.EventEnvelope;
import blps.itmo.platform.events.EventType;
import blps.itmo.platform.events.TopicNames;
import blps.itmo.platform.events.payload.PenaltyApplicationRequestedPayload;

@Component
public class PenaltyRequestListener {

    private final ObjectMapper objectMapper;
    private final PenaltyWorkflowService penaltyWorkflowService;

    public PenaltyRequestListener(ObjectMapper objectMapper, PenaltyWorkflowService penaltyWorkflowService) {
        this.objectMapper = objectMapper;
        this.penaltyWorkflowService = penaltyWorkflowService;
    }

    @KafkaListener(topics = TopicNames.PENALTY_EVENTS, groupId = "penalty-service")
    public void onPenaltyEvent(String rawEvent) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(rawEvent, EventEnvelope.class);
        if (envelope.getEventType() != EventType.PENALTY_APPLICATION_REQUESTED) {
            return;
        }
        penaltyWorkflowService.handlePenaltyRequested(
                envelope,
                objectMapper.treeToValue(envelope.getPayload(), PenaltyApplicationRequestedPayload.class));
    }
}
