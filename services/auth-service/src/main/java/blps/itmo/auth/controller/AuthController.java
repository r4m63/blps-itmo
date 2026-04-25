package blps.itmo.auth.controller;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/internal/users/{id}")
    public RemoteUserView getUser(@PathVariable Long id) {
        return authUserService.getUserView(id);
    }

    @GetMapping("/api/auth/users")
    public List<RemoteUserView> listUsers() {
        return authUserService.listUsers();
    }

    @PostMapping("/api/auth/users/{id}/deactivate")
    public RemoteUserView deactivateUser(@PathVariable Long id,
            @RequestBody(required = false) DeactivateUserRequest request) {
        return authUserService.deactivateUser(id, request == null ? null : request.reason());
    }

    @RequestMapping("/actuator/healthz")
    public String healthz() {
        return "ok";
    }

    public record DeactivateUserRequest(String reason) {
    }
}
