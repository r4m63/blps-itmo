package blps.itmo.claim.kafka;

import blps.itmo.claim.jca.jira.JiraIssueService;
import blps.itmo.claim.kafka.config.TopicNames;
import blps.itmo.claim.kafka.payload.ClaimCreatedPayload;
import blps.itmo.claim.kafka.processedmessage.ProcessedMessageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JiraEventConsumer {

    private final ObjectMapper objectMapper;
    private final ProcessedMessageService processedMessageService;
    private final JiraIssueService jiraIssueService;

    @KafkaListener(topics = TopicNames.CLAIM_EVENTS, groupId = "claim-jira")
    @Transactional
    public void onClaimEvent(String message) throws Exception {
        EventEnvelope<ClaimCreatedPayload> envelope = objectMapper.readValue(
                message,
                new TypeReference<EventEnvelope<ClaimCreatedPayload>>() {
                }
        );
        if (!EventType.CLAIM_CREATED.equals(envelope.eventType())) {
            return;
        }
        if (processedMessageService.alreadyProcessed(envelope.eventId())) {
            return;
        }
        jiraIssueService.createClaimIssue(envelope.payload());
        processedMessageService.markProcessed(envelope.eventId());
    }
}
