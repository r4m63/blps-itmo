package blps.itmo.claim.kafka.payload;

public record UserDeactivatedPayload(
        Integer userId,
        String reason
) {
}
