package blps.itmo.gateway.api.http;

import java.math.BigDecimal;

import blps.itmo.gateway.grpc.GatewayGrpcClients;
import blps.itmo.gateway.exceptions.GatewayForbiddenException;
import blps.itmo.gateway.security.GatewayAuthFilter;
import jakarta.servlet.http.HttpServletRequest;

abstract class GatewayControllerSupport {

    protected final GatewayGrpcClients grpcClients;

    protected GatewayControllerSupport(GatewayGrpcClients grpcClients) {
        this.grpcClients = grpcClients;
    }

    protected long actorUserId(HttpServletRequest request) {
        return GatewayAuthFilter.userId(request);
    }

    protected String actorRole(HttpServletRequest request) {
        return GatewayAuthFilter.userRole(request);
    }

    protected void requireRole(HttpServletRequest request, String... roles) {
        String role = GatewayAuthFilter.userRole(request);
        for (String allowed : roles) {
            if (allowed.equals(role)) {
                return;
            }
        }
        throw new GatewayForbiddenException("Role " + role + " is not allowed");
    }

    protected String money(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}
