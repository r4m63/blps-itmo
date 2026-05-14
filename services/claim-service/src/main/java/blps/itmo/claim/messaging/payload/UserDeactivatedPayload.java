package blps.itmo.claim.messaging.payload;

public record UserDeactivatedPayload(
        Integer userId,
        String reason
) {
}
