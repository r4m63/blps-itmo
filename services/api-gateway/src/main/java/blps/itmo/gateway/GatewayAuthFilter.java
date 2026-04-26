package blps.itmo.gateway;

import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import blps.itmo.platform.security.DemoJwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class GatewayAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_USER_ID = "gateway.userId";
    public static final String ATTR_USER_ROLE = "gateway.userRole";
    public static final String ATTR_CORRELATION_ID = "gateway.correlationId";

    private final DemoJwtService jwtService;

    public GatewayAuthFilter(ObjectMapper objectMapper,
            @Value("${app.security.jwt-secret:lab3-demo-secret}") String jwtSecret) {
        this.jwtService = new DemoJwtService(objectMapper, jwtSecret);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = headerOrGenerated(request, "X-Correlation-Id");
        request.setAttribute(ATTR_CORRELATION_ID, correlationId);
        response.setHeader("X-Correlation-Id", correlationId);

        if (isPublic(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing Bearer token");
            return;
        }
        try {
            DemoJwtService.Claims claims = jwtService.validate(authorization.substring("Bearer ".length()));
            request.setAttribute(ATTR_USER_ID, claims.userId().toString());
            request.setAttribute(ATTR_USER_ROLE, claims.role());
            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
        }
    }

    private boolean isPublic(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/auth/login")
                || path.equals("/actuator/health")
                || path.equals("/actuator/healthz")
                || path.equals("/healthz");
    }

    private String headerOrGenerated(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }
}
