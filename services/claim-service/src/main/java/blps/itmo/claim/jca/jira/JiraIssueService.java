package blps.itmo.claim.jca.jira;

import blps.itmo.claim.kafka.payload.ClaimCreatedPayload;
import jakarta.resource.ResourceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "jira", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class JiraIssueService {

    private final JiraConnectionFactory connectionFactory;

    @Value("${jira.project-key}")
    private String projectKey;

    public void createClaimIssue(ClaimCreatedPayload p) {
        String summary = "[BLPS Claim #%d] %s".formatted(p.claimId(), p.title());
        String description = """
                *Claim ID:* %d
                *Status:* SUBMITTED
                *Landlord (user id):* %d
                *Tenant   (user id):* %d
                *Claimed amount:* %s %s

                *Title:*
                %s

                *Description:*
                %s

                ----
                _Created automatically by BLPS claim-service via JCA → Jira REST API._"""
                .formatted(
                        p.claimId(),
                        p.landlordId(),
                        p.tenantId(),
                        p.claimedAmount(),
                        p.currency(),
                        p.title(),
                        p.description()
                );
        List<String> labels = List.of("blps", "claim", "auto-created", "claim-" + p.claimId());

        JiraConnection connection = null;
        try {
            connection = connectionFactory.getConnection();
            String issueKey = connection.createIssue(projectKey, summary, description, labels);
            log.info("Jira issue {} created for claim {}", issueKey, p.claimId());
        } catch (RuntimeException ex) {
            log.warn("Failed to create Jira issue for claim {}: {}", p.claimId(), ex.getMessage());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (ResourceException e) {
                    log.warn("Failed to close Jira connection: {}", e.getMessage());
                }
            }
        }
    }
}
