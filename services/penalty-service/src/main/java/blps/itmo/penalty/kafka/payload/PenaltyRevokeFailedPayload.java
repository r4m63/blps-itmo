package blps.itmo.penalty.kafka.payload;

public record PenaltyRevokeFailedPayload(
        Integer claimId,
        Integer operationId,
        String reason
) {
}
