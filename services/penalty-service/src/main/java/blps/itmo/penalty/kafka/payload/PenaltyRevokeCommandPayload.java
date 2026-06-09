package blps.itmo.penalty.kafka.payload;

public record PenaltyRevokeCommandPayload(
        Integer claimId,
        Integer operationId,
        String reason
) {
}
