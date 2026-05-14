package blps.itmo.claim.jca.jira;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConnectionEvent;
import jakarta.resource.spi.ConnectionEventListener;
import jakarta.resource.spi.LocalTransaction;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionMetaData;
import jakarta.transaction.xa.XAResource;

import javax.security.auth.Subject;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

public class JiraManagedConnection implements ManagedConnection {

    private final JiraConnectionImpl connection;
    private final Set<ConnectionEventListener> listeners = new HashSet<>();

    public JiraManagedConnection(String baseUrl, String user, String token) {
        this.connection = new JiraConnectionImpl(baseUrl, user, token, this);
    }

    @Override
    public Object getConnection(Subject subject, jakarta.resource.spi.ConnectionRequestInfo cxRequestInfo)
            throws ResourceException {
        return connection;
    }

    @Override
    public void destroy() throws ResourceException {
        // no-op
    }

    @Override
    public void cleanup() throws ResourceException {
        // no-op
    }

    @Override
    public void associateConnection(Object connection) throws ResourceException {
        // no-op
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener listener) {
        listeners.remove(listener);
    }

    public void close() throws ResourceException {
        ConnectionEvent event = new ConnectionEvent(this, ConnectionEvent.CONNECTION_CLOSED);
        for (ConnectionEventListener listener : listeners) {
            listener.connectionClosed(event);
        }
    }

    @Override
    public void setLogWriter(PrintWriter out) throws ResourceException {
        // no-op
    }

    @Override
    public PrintWriter getLogWriter() throws ResourceException {
        return null;
    }

    @Override
    public ManagedConnectionMetaData getMetaData() throws ResourceException {
        return new JiraManagedConnectionMetaData();
    }

    @Override
    public LocalTransaction getLocalTransaction() throws ResourceException {
        return null;
    }

    @Override
    public XAResource getXAResource() throws ResourceException {
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
