package blps.itmo.claim.jca.jira;

import jakarta.resource.cci.Connection;

import java.util.List;

public interface JiraConnection extends Connection, AutoCloseable {

    String createIssue(String projectKey, String summary, String description, List<String> labels);
}
