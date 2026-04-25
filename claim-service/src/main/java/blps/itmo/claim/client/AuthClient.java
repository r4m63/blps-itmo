package blps.itmo.claim.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import blps.itmo.platform.events.RemoteUserView;

@Component
public class AuthClient {

    private final RestClient restClient;

    public AuthClient(@Value("${app.auth-service-url:http://localhost:8082}") String authServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(authServiceUrl)
                .build();
    }

    public RemoteUserView requireUser(Long userId, String expectedRole) {
        try {
            RemoteUserView user = restClient.get()
                    .uri("/internal/users/{id}", userId)
                    .retrieve()
                    .body(RemoteUserView.class);
            if (user == null) {
                throw new IllegalArgumentException("User not found: " + userId);
            }
            if (!expectedRole.equals(user.getRole())) {
                throw new IllegalArgumentException("Expected role " + expectedRole + " for user " + userId);
            }
            if (!user.isEnabled()) {
                throw new IllegalArgumentException("User is disabled: " + userId);
            }
            return user;
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Auth service returned " + e.getRawStatusCode() + " for user " + userId, e);
        }
    }
}
