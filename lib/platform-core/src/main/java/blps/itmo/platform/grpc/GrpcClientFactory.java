package blps.itmo.platform.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public final class GrpcClientFactory {

    private GrpcClientFactory() {
    }

    public static ManagedChannel plaintextChannel(String target) {
        return ManagedChannelBuilder.forTarget(target).usePlaintext().build();
    }
}
