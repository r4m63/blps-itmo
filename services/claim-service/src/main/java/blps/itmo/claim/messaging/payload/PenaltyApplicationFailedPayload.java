package blps.itmo.claim.messaging.payload;

public record PenaltyApplicationFailedPayload(
        Integer claimId,
        Integer operationId,
        String reason
) {
}
