package blps.itmo.auth.messaging.payload;

public record UserDeactivatedPayload(
        Integer userId,
        String reason
) {
}
