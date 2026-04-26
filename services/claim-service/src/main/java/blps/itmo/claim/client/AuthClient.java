package blps.itmo.claim.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import blps.itmo.grpc.AuthRpcServiceGrpc;
import blps.itmo.grpc.GetUserRequest;
import blps.itmo.platform.events.RemoteUserView;
import blps.itmo.platform.grpc.GrpcClientFactory;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;

@Component
public class AuthClient {

    private final ManagedChannel channel;
    private final AuthRpcServiceGrpc.AuthRpcServiceBlockingStub authStub;

    public AuthClient(@Value("${app.grpc.clients.auth.target:localhost:19082}") String authGrpcTarget) {
        this.channel = GrpcClientFactory.plaintextChannel(authGrpcTarget);
        this.authStub = AuthRpcServiceGrpc.newBlockingStub(channel);
    }

    public RemoteUserView requireUser(Long userId, String expectedRole) {
        try {
            var user = authStub.getUser(GetUserRequest.newBuilder().setUserId(userId).build());
            if (!expectedRole.equals(user.getRole())) {
                throw new IllegalArgumentException("Expected role " + expectedRole + " for user " + userId);
            }
            if (!user.getEnabled()) {
                throw new IllegalArgumentException("User is disabled: " + userId);
            }
            return RemoteUserView.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .enabled(user.getEnabled())
                    .penaltyCount(user.getPenaltyCount())
                    .build();
        } catch (StatusRuntimeException e) {
            throw new IllegalStateException("Auth gRPC lookup failed for user " + userId + ": "
                    + e.getStatus().getDescription(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        channel.shutdown();
    }
}
