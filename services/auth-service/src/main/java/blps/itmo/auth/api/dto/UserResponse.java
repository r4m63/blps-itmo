package blps.itmo.auth.api.dto;

import blps.itmo.auth.domain.User;
import blps.itmo.auth.domain.UserRole;

import java.time.Instant;

public record UserResponse(
        Integer id,
        String email,
        UserRole role,
        boolean enabled,
        Integer penaltyCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(),
                u.getEmail(),
                u.getRole(),
                u.isEnabled(),
                u.getPenaltyCount(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }
}
