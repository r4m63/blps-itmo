package blps.itmo.platform.grpc;

import org.slf4j.MDC;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

public final class CorrelationIdGrpcInterceptors {

    public static final String MDC_KEY = "correlationId";
    public static final Metadata.Key<String> CORRELATION_ID_KEY =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);
    public static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationIdGrpcInterceptors() {
    }

    public static ClientInterceptor client() {
        return new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method,
                    CallOptions callOptions,
                    Channel next) {
                return new ForwardingClientCall.SimpleForwardingClientCall<>(
                        next.newCall(method, callOptions)) {
                    @Override
                    public void start(Listener<RespT> responseListener, Metadata headers) {
                        String correlationId = CURRENT.get();
                        if (correlationId != null && !correlationId.isBlank()) {
                            headers.put(CORRELATION_ID_KEY, correlationId);
                        }
                        super.start(responseListener, headers);
                    }
                };
            }
        };
    }

    public static ServerInterceptor server() {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                String correlationId = headers.get(CORRELATION_ID_KEY);
                ServerCall.Listener<ReqT> listener = withCorrelation(correlationId, () -> next.startCall(call, headers));
                return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
                    @Override
                    public void onMessage(ReqT message) {
                        withCorrelation(correlationId, () -> {
                            super.onMessage(message);
                            return null;
                        });
                    }

                    @Override
                    public void onHalfClose() {
                        withCorrelation(correlationId, () -> {
                            super.onHalfClose();
                            return null;
                        });
                    }

                    @Override
                    public void onCancel() {
                        withCorrelation(correlationId, () -> {
                            super.onCancel();
                            return null;
                        });
                    }

                    @Override
                    public void onComplete() {
                        withCorrelation(correlationId, () -> {
                            super.onComplete();
                            return null;
                        });
                    }

                    @Override
                    public void onReady() {
                        withCorrelation(correlationId, () -> {
                            super.onReady();
                            return null;
                        });
                    }
                };
            }
        };
    }

    public static void setCurrent(String correlationId) {
        CURRENT.set(correlationId);
        MDC.put(MDC_KEY, correlationId);
    }

    public static void clearCurrent() {
        CURRENT.remove();
        MDC.remove(MDC_KEY);
    }

    private static <T> T withCorrelation(String correlationId, SupplierWithException<T> action) {
        String previous = CURRENT.get();
        String previousMdc = MDC.get(MDC_KEY);
        try {
            if (correlationId != null && !correlationId.isBlank()) {
                CURRENT.set(correlationId);
                MDC.put(MDC_KEY, correlationId);
            }
            return action.get();
        } finally {
            restore(previous, previousMdc);
        }
    }

    private static void restore(String previous, String previousMdc) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
        if (previousMdc == null) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, previousMdc);
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get();
    }
}
