package blps.itmo.platform.grpc;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

@Component
public class GrpcServerLifecycle implements SmartLifecycle {

    private final List<BindableService> services;
    private final int port;
    private final boolean enabled;
    private Server server;
    private boolean running;

    public GrpcServerLifecycle(List<BindableService> services,
            @Value("${app.grpc.server.port:0}") int port,
            @Value("${app.grpc.server.enabled:true}") boolean enabled) {
        this.services = services;
        this.port = port;
        this.enabled = enabled;
    }

    @Override
    public void start() {
        if (!enabled || services.isEmpty() || port <= 0) {
            return;
        }
        ServerBuilder<?> builder = ServerBuilder.forPort(port);
        services.forEach(builder::addService);
        try {
            server = builder.build().start();
            running = true;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start gRPC server on port " + port, e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();
            server = null;
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
