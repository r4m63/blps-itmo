package blps.itmo.claim.jca.jira;

import jakarta.resource.cci.ConnectionFactory;

public interface JiraConnectionFactory extends ConnectionFactory {

    @Override
    JiraConnection getConnection();
}
