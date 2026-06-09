package blps.itmo.claim.kafka.payload;

public record PenaltyRevokeCommandPayload(
        Integer claimId,
        Integer operationId,
        String reason
) {
}
