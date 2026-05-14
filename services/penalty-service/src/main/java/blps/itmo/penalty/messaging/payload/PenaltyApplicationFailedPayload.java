package blps.itmo.penalty.messaging.payload;

public record PenaltyApplicationFailedPayload(
        Integer claimId,
        Integer operationId,
        String reason
) {
}
