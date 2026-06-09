package blps.itmo.auth.api;

import blps.itmo.auth.api.dto.MeResponse;
import blps.itmo.auth.api.dto.RegisterRequest;
import blps.itmo.auth.api.dto.UserResponse;
import blps.itmo.auth.domain.User;
import blps.itmo.auth.security.AppUserDetails;
import blps.itmo.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
        User user = userService.register(req);
        return ResponseEntity.created(URI.create("/api/users/" + user.getId()))
                .body(UserResponse.from(user));
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AppUserDetails principal) {
        return MeResponse.from(principal.getUser(), principal.getPrivilegeCodes());
    }
}
