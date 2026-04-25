package blps.itmo.auth.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import blps.itmo.platform.events.EventEnvelope;
import blps.itmo.platform.events.EventType;
import blps.itmo.platform.events.TopicNames;
import blps.itmo.platform.events.payload.PenaltyAppliedPayload;

@Component
public class PenaltyEventListener {

    private final ObjectMapper objectMapper;
    private final AuthUserService authUserService;

    public PenaltyEventListener(ObjectMapper objectMapper, AuthUserService authUserService) {
        this.objectMapper = objectMapper;
        this.authUserService = authUserService;
    }

    @KafkaListener(topics = TopicNames.PENALTY_EVENTS, groupId = "auth-service")
    public void onPenaltyEvent(String rawEvent) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(rawEvent, EventEnvelope.class);
        if (envelope.getEventType() != EventType.PENALTY_APPLIED) {
            return;
        }
        PenaltyAppliedPayload payload = objectMapper.treeToValue(envelope.getPayload(), PenaltyAppliedPayload.class);
        authUserService.handlePenaltyApplied(envelope, payload);
    }
}
