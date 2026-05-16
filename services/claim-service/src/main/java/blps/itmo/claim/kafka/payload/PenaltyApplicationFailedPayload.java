package blps.itmo.claim.kafka.payload;

public record PenaltyApplicationFailedPayload(
        Integer claimId,
        Integer operationId,
        String reason
) {
}
