package blps.itmo.gateway.api.http;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import blps.itmo.gateway.grpc.GatewayGrpcClients;
import blps.itmo.gateway.dto.GatewayDtoMapper;
import blps.itmo.gateway.dto.GatewayDtos.DeactivateUserHttpRequest;
import blps.itmo.gateway.dto.GatewayDtos.LoginHttpRequest;
import blps.itmo.gateway.dto.GatewayDtos.LoginHttpResponse;
import blps.itmo.gateway.dto.GatewayDtos.UserHttpResponse;
import blps.itmo.grpc.DeactivateUserRequest;
import blps.itmo.grpc.EmptyRequest;
import blps.itmo.grpc.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthGatewayController extends GatewayControllerSupport {

    public AuthGatewayController(GatewayGrpcClients grpcClients) {
        super(grpcClients);
    }

    @PostMapping("/login")
    public LoginHttpResponse login(@Valid @RequestBody LoginHttpRequest request) {
        var response = grpcClients.auth().login(LoginRequest.newBuilder()
                .setUserId(request.userId() == null ? 0 : request.userId())
                .setEmail(request.email() == null ? "" : request.email())
                .build());
        return new LoginHttpResponse(response.getToken(), GatewayDtoMapper.toHttp(response.getUser()));
    }

    @GetMapping("/users")
    public List<UserHttpResponse> listUsers(HttpServletRequest servletRequest) {
        requireRole(servletRequest, "ADMIN");
        return grpcClients.auth().listUsers(EmptyRequest.getDefaultInstance()).getUsersList().stream()
                .map(GatewayDtoMapper::toHttp)
                .toList();
    }

    @PostMapping("/users/{id}/deactivate")
    public UserHttpResponse deactivateUser(@PathVariable Long id,
            @Valid @RequestBody(required = false) DeactivateUserHttpRequest request,
            HttpServletRequest servletRequest) {
        requireRole(servletRequest, "ADMIN");
        var response = grpcClients.auth().deactivateUser(DeactivateUserRequest.newBuilder()
                .setUserId(id)
                .setReason(request == null || request.reason() == null ? "" : request.reason())
                .setActorRole(actorRole(servletRequest))
                .build());
        return GatewayDtoMapper.toHttp(response);
    }
}
