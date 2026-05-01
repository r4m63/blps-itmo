package blps.itmo.gateway.infrastructure.grpc;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import blps.itmo.grpc.AuditRpcServiceGrpc;
import blps.itmo.grpc.AuthRpcServiceGrpc;
import blps.itmo.grpc.ClaimRpcServiceGrpc;
import blps.itmo.grpc.NotificationRpcServiceGrpc;
import blps.itmo.grpc.PenaltyRpcServiceGrpc;
import blps.itmo.grpc.StorageRpcServiceGrpc;
import blps.itmo.platform.grpc.CorrelationIdGrpcInterceptors;
import blps.itmo.platform.grpc.GrpcClientFactory;
import io.grpc.ManagedChannel;
import jakarta.annotation.PreDestroy;

/**
 * ЦЕНТРАЛИЗОВАННОЕ УПРАВЛЕНИЕ gRPC КЛИЕНТАМИ
 *
 * Создает ManagedChannel'ы (соединения) ко всем бекенд-сервисам
 * и предоставляет удобные методы для получения gRPC stub'ов.
 *
 * Архитектура соединений:
 * =======================
 *   Gateway --- gRPC ---> auth-service    (порт 19082)
 *   Gateway --- gRPC ---> claim-service   (порт 19081)
 *   Gateway --- gRPC ---> penalty-service (порт 19084)
 *   Gateway --- gRPC ---> storage-service (порт 19087)
 *   Gateway --- gRPC ---> audit-service   (порт 19086)
 *
 * Почему блокирующие stub'ы (BlockingStub)?
 * =========================================
 * Spring MVC (этот gateway) работает в синхронном режиме на один поток на запрос.
 * Блокирующий stub идеально подходит — каждый HTTP запрос занимает тред и
 * синхронно ждет ответа от gRPC.
 *
 * Если бы использовали асинхронный stub (Netty + реактив), то не получили бы
 * преимущества, потому что томкат все равно блокирующий.
 */
@Component
public class GatewayGrpcClients {

    private final ManagedChannel authChannel;
    private final ManagedChannel claimChannel;
    private final ManagedChannel penaltyChannel;
    private final ManagedChannel storageChannel;
    private final ManagedChannel auditChannel;
    private final ManagedChannel notificationChannel;
    private final long deadlineSeconds;

    public GatewayGrpcClients(
            @Value("${app.grpc.clients.auth.target:localhost:19082}") String authTarget,
            @Value("${app.grpc.clients.claim.target:localhost:19081}") String claimTarget,
            @Value("${app.grpc.clients.penalty.target:localhost:19084}") String penaltyTarget,
            @Value("${app.grpc.clients.storage.target:localhost:19087}") String storageTarget,
            @Value("${app.grpc.clients.audit.target:localhost:19086}") String auditTarget,
            @Value("${app.grpc.clients.notification.target:localhost:19085}") String notificationTarget,
            @Value("${app.grpc.deadline-seconds:5}") long deadlineSeconds) {
        this.authChannel = GrpcClientFactory.plaintextChannel(authTarget, CorrelationIdGrpcInterceptors.client());
        this.claimChannel = GrpcClientFactory.plaintextChannel(claimTarget, CorrelationIdGrpcInterceptors.client());
        this.penaltyChannel = GrpcClientFactory.plaintextChannel(penaltyTarget, CorrelationIdGrpcInterceptors.client());
        this.storageChannel = GrpcClientFactory.plaintextChannel(storageTarget, CorrelationIdGrpcInterceptors.client());
        this.auditChannel = GrpcClientFactory.plaintextChannel(auditTarget, CorrelationIdGrpcInterceptors.client());
        this.notificationChannel = GrpcClientFactory.plaintextChannel(notificationTarget,
                CorrelationIdGrpcInterceptors.client());
        this.deadlineSeconds = deadlineSeconds;
    }

    public AuthRpcServiceGrpc.AuthRpcServiceBlockingStub auth() {
        return AuthRpcServiceGrpc.newBlockingStub(authChannel)
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
    }

    public ClaimRpcServiceGrpc.ClaimRpcServiceBlockingStub claim() {
        return ClaimRpcServiceGrpc.newBlockingStub(claimChannel)
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
    }

    public PenaltyRpcServiceGrpc.PenaltyRpcServiceBlockingStub penalty() {
        return PenaltyRpcServiceGrpc.newBlockingStub(penaltyChannel)
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
    }

    public StorageRpcServiceGrpc.StorageRpcServiceBlockingStub storage() {
        return StorageRpcServiceGrpc.newBlockingStub(storageChannel)
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
    }

    public AuditRpcServiceGrpc.AuditRpcServiceBlockingStub audit() {
        return AuditRpcServiceGrpc.newBlockingStub(auditChannel)
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
    }

    public NotificationRpcServiceGrpc.NotificationRpcServiceBlockingStub notification() {
        return NotificationRpcServiceGrpc.newBlockingStub(notificationChannel)
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        List.of(authChannel, claimChannel, penaltyChannel, storageChannel, auditChannel, notificationChannel)
                .forEach(ManagedChannel::shutdown);
    }
}
