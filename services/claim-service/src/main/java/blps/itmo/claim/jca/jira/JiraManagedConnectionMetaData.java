package blps.itmo.claim.jca.jira;

import jakarta.resource.spi.ManagedConnectionMetaData;

public class JiraManagedConnectionMetaData implements ManagedConnectionMetaData {

    @Override
    public String getEISProductName() {
        return "Jira";
    }

    @Override
    public String getEISProductVersion() {
        return "cloud";
    }

    @Override
    public int getMaxConnections() {
        return 10;
    }

    @Override
    public String getUserName() {
        return "jira";
    }
}
