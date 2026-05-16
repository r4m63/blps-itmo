package blps.itmo.claim.kafka.payload;

public record PenaltyRevokeFailedPayload(
        Integer claimId,
        Integer operationId,
        String reason
) {
}
