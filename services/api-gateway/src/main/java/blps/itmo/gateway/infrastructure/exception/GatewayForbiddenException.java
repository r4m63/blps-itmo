package blps.itmo.gateway.infrastructure.exception;

public class GatewayForbiddenException extends RuntimeException {

    public GatewayForbiddenException(String message) {
        super(message);
    }
}
