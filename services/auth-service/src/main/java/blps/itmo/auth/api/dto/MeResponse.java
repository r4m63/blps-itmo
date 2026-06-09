package blps.itmo.auth.api.dto;

import blps.itmo.auth.domain.User;
import blps.itmo.auth.domain.UserRole;

import java.util.List;

public record MeResponse(
        Integer id,
        String email,
        UserRole role,
        boolean enabled,
        Integer penaltyCount,
        List<String> privileges
) {
    public static MeResponse from(User u, List<String> privileges) {
        return new MeResponse(
                u.getId(),
                u.getEmail(),
                u.getRole(),
                u.isEnabled(),
                u.getPenaltyCount(),
                privileges
        );
    }
}
