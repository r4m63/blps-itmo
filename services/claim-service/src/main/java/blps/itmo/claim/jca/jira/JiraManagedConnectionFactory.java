package blps.itmo.claim.jca.jira;

import jakarta.resource.ResourceException;
import jakarta.resource.cci.ConnectionFactory;
import jakarta.resource.spi.ConnectionManager;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionFactory;
import jakarta.resource.spi.ResourceAdapterInternalException;
import jakarta.resource.spi.ValidatingManagedConnectionFactory;
import lombok.Getter;
import lombok.Setter;

import javax.naming.Reference;
import javax.naming.Referenceable;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class JiraManagedConnectionFactory implements ManagedConnectionFactory, Referenceable, Serializable,
        ValidatingManagedConnectionFactory {

    private String baseUrl;
    private String user;
    private String token;

    private Reference reference;
    private PrintWriter logWriter;

    @Override
    public Object createConnectionFactory(ConnectionManager cxManager) throws ResourceException {
        return new JiraConnectionFactoryImpl(cxManager, this);
    }

    @Override
    public Object createConnectionFactory() throws ResourceException {
        throw new ResourceAdapterInternalException("ConnectionManager required");
    }

    @Override
    public ManagedConnection createManagedConnection(javax.security.auth.Subject subject,
                                                     ConnectionRequestInfo cxRequestInfo) {
        return new JiraManagedConnection(baseUrl, user, token);
    }

    @Override
    public ManagedConnection matchManagedConnections(Set connectionSet,
                                                     javax.security.auth.Subject subject,
                                                     ConnectionRequestInfo cxRequestInfo) {
        if (connectionSet == null || connectionSet.isEmpty()) {
            return null;
        }
        return (ManagedConnection) connectionSet.iterator().next();
    }

    @Override
    public Set getInvalidConnections(Set connectionSet) {
        return new HashSet();
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
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        this.logWriter = out;
    }

    @Override
    public int hashCode() {
        return (baseUrl + user).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        JiraManagedConnectionFactory other = (JiraManagedConnectionFactory) obj;
        return baseUrl.equals(other.baseUrl) && user.equals(other.user);
    }
}
