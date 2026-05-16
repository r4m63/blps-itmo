package blps.itmo.claim.jca.jira;

import blps.itmo.claim.kafka.payload.ClaimCreatedPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JiraIssueService {

    private final JiraConnectionFactory connectionFactory;

    @Value("${jira.project-key}")
    private String projectKey;

    public void createClaimIssue(ClaimCreatedPayload payload) {
        try (JiraConnection connection = connectionFactory.getConnection()) {
            String summary = "Claim #" + payload.claimId() + " created";
            String description = "Landlord: " + payload.landlordId()
                    + "\nTenant: " + payload.tenantId()
                    + "\nTitle: " + payload.title()
                    + "\nAmount: " + payload.claimedAmount() + " " + payload.currency();
            connection.createIssue(projectKey, summary, description);
        }
    }
}
