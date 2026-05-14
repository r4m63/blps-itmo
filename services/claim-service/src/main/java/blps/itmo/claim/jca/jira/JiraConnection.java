package blps.itmo.claim.jca.jira;

import jakarta.resource.cci.Connection;

public interface JiraConnection extends Connection {

    void createIssue(String projectKey, String summary, String description);
}
