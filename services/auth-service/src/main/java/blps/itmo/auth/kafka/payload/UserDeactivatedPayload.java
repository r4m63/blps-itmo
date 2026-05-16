package blps.itmo.auth.kafka.payload;

public record UserDeactivatedPayload(
        Integer userId,
        String reason
) {
}
