package blps.itmo.platform.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

public final class GrpcErrors {

    private GrpcErrors() {
    }

    public static StatusRuntimeException toStatus(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException statusRuntimeException) {
            return statusRuntimeException;
        }
        if (throwable instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(throwable.getMessage()).withCause(throwable).asRuntimeException();
        }
        if (throwable instanceof IllegalStateException) {
            return Status.FAILED_PRECONDITION.withDescription(throwable.getMessage()).withCause(throwable).asRuntimeException();
        }
        return Status.INTERNAL.withDescription(throwable.getMessage()).withCause(throwable).asRuntimeException();
    }
}
