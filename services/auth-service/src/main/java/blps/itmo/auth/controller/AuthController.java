package blps.itmo.auth.controller;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import blps.itmo.auth.service.AuthUserService;
import blps.itmo.platform.events.RemoteUserView;

@RestController
public class AuthController {

    private final AuthUserService authUserService;

    public AuthController(AuthUserService authUserService) {
        this.authUserService = authUserService;
    }

    @Bean
    public org.springframework.boot.ApplicationRunner authSeedRunner() {
        return authUserService.seedUsers();
    }

    @GetMapping("/api/auth/users")
    public List<RemoteUserView> listUsers() {
        return authUserService.listUsers();
    }

    @PostMapping("/api/auth/login")
    public AuthUserService.LoginResult login(@RequestBody LoginRequest request) {
        return authUserService.login(request.userId(), request.email());
    }

    @PostMapping("/api/auth/users/{id}/deactivate")
    public RemoteUserView deactivateUser(@PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String actorRole,
            @RequestBody(required = false) DeactivateUserRequest request) {
        requireAdmin(actorRole);
        return authUserService.deactivateUser(id, request == null ? null : request.reason());
    }

    @RequestMapping("/actuator/healthz")
    public String healthz() {
        return "ok";
    }

    public record DeactivateUserRequest(String reason) {
    }

    public record LoginRequest(Long userId, String email) {
    }

    private void requireAdmin(String actorRole) {
        if (actorRole != null && !"ADMIN".equals(actorRole)) {
            throw new IllegalArgumentException("ADMIN role is required");
        }
    }
}
