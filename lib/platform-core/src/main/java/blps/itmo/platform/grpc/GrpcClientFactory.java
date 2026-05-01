package blps.itmo.platform.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

// ФАБРИКА ДЛЯ СОЗДАНИЯ gRPC КЛИЕНТОВ.
public final class GrpcClientFactory {

    private GrpcClientFactory() {
    }

    /**
     * Создает gRPC канал без шифрования (plaintext).
     *
     * ВАЖНО: usePlaintext() отключает TLS/SSL!
     * - Для локальной разработки и внутри Kubernetes кластера — ок
     * - Для production через интернет — НЕТ! (нужен SSL)
     */
    public static ManagedChannel plaintextChannel(String target) {
        return ManagedChannelBuilder.forTarget(target).usePlaintext().build();
    }
}
