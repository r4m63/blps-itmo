package blps.itmo.gateway;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import blps.itmo.grpc.AuditRpcServiceGrpc;
import blps.itmo.grpc.AuthRpcServiceGrpc;
import blps.itmo.grpc.ClaimRpcServiceGrpc;
import blps.itmo.grpc.PenaltyRpcServiceGrpc;
import blps.itmo.grpc.StorageRpcServiceGrpc;
import blps.itmo.platform.grpc.GrpcClientFactory;
import io.grpc.ManagedChannel;
import jakarta.annotation.PreDestroy;

@Component
public class GatewayGrpcClients {

    private final ManagedChannel authChannel;
    private final ManagedChannel claimChannel;
    private final ManagedChannel penaltyChannel;
    private final ManagedChannel storageChannel;
    private final ManagedChannel auditChannel;

    public GatewayGrpcClients(
            @Value("${app.grpc.clients.auth.target:localhost:19082}") String authTarget,
            @Value("${app.grpc.clients.claim.target:localhost:19081}") String claimTarget,
            @Value("${app.grpc.clients.penalty.target:localhost:19084}") String penaltyTarget,
            @Value("${app.grpc.clients.storage.target:localhost:19087}") String storageTarget,
            @Value("${app.grpc.clients.audit.target:localhost:19086}") String auditTarget) {
        this.authChannel = GrpcClientFactory.plaintextChannel(authTarget);
        this.claimChannel = GrpcClientFactory.plaintextChannel(claimTarget);
        this.penaltyChannel = GrpcClientFactory.plaintextChannel(penaltyTarget);
        this.storageChannel = GrpcClientFactory.plaintextChannel(storageTarget);
        this.auditChannel = GrpcClientFactory.plaintextChannel(auditTarget);
    }

    public AuthRpcServiceGrpc.AuthRpcServiceBlockingStub auth() {
        return AuthRpcServiceGrpc.newBlockingStub(authChannel);
    }

    public ClaimRpcServiceGrpc.ClaimRpcServiceBlockingStub claim() {
        return ClaimRpcServiceGrpc.newBlockingStub(claimChannel);
    }

    public PenaltyRpcServiceGrpc.PenaltyRpcServiceBlockingStub penalty() {
        return PenaltyRpcServiceGrpc.newBlockingStub(penaltyChannel);
    }

    public StorageRpcServiceGrpc.StorageRpcServiceBlockingStub storage() {
        return StorageRpcServiceGrpc.newBlockingStub(storageChannel);
    }

    public AuditRpcServiceGrpc.AuditRpcServiceBlockingStub audit() {
        return AuditRpcServiceGrpc.newBlockingStub(auditChannel);
    }

    @PreDestroy
    public void shutdown() {
        List.of(authChannel, claimChannel, penaltyChannel, storageChannel, auditChannel)
                .forEach(ManagedChannel::shutdown);
    }
}
