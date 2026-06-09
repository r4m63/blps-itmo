package blps.itmo.claim.jca.jira;

import jakarta.resource.cci.Connection;
import jakarta.resource.cci.ConnectionSpec;
import jakarta.resource.spi.ConnectionManager;
import jakarta.resource.spi.ManagedConnectionFactory;

import javax.naming.Reference;
import javax.naming.Referenceable;
import java.io.Serializable;

public class JiraConnectionFactoryImpl implements JiraConnectionFactory, Referenceable, Serializable {

    private final ConnectionManager connectionManager;
    private final ManagedConnectionFactory managedConnectionFactory;
    private Reference reference;

    public JiraConnectionFactoryImpl(ConnectionManager connectionManager, ManagedConnectionFactory managedConnectionFactory) {
        this.connectionManager = connectionManager;
        this.managedConnectionFactory = managedConnectionFactory;
    }

    @Override
    public JiraConnection getConnection() {
        try {
            return (JiraConnection) connectionManager.allocateConnection(managedConnectionFactory, null);
        } catch (jakarta.resource.ResourceException e) {
            throw new IllegalStateException("failed to allocate JCA connection to Jira", e);
        }
    }

    @Override
    public Connection getConnection(ConnectionSpec properties) throws jakarta.resource.ResourceException {
        return getConnection();
    }

    @Override
    public Reference getReference() {
        return reference;
    }

    @Override
    public void setReference(Reference reference) {
        this.reference = reference;
    }

    @Override
    public jakarta.resource.cci.RecordFactory getRecordFactory() {
        return null;
    }

    @Override
    public jakarta.resource.cci.ResourceAdapterMetaData getMetaData() {
        return null;
    }
}
