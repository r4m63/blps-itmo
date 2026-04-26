package blps.itmo.storage.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import blps.itmo.platform.events.EventEnvelope;
import blps.itmo.platform.events.EventType;
import blps.itmo.platform.events.TopicNames;
import blps.itmo.platform.events.payload.AttachmentBindingRequestedPayload;

@Component
public class ClaimEventListener {

    private final ObjectMapper objectMapper;
    private final StorageWorkflowService storageWorkflowService;

    public ClaimEventListener(ObjectMapper objectMapper, StorageWorkflowService storageWorkflowService) {
        this.objectMapper = objectMapper;
        this.storageWorkflowService = storageWorkflowService;
    }

    @KafkaListener(topics = TopicNames.CLAIM_EVENTS, groupId = "storage-service")
    public void onClaimEvent(String rawEvent) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(rawEvent, EventEnvelope.class);
        if (envelope.getEventType() != EventType.ATTACHMENT_BINDING_REQUESTED) {
            return;
        }
        storageWorkflowService.handleBindingRequested(
                envelope,
                objectMapper.treeToValue(envelope.getPayload(), AttachmentBindingRequestedPayload.class));
    }
}
