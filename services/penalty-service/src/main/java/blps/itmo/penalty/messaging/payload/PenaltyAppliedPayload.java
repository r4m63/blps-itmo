package blps.itmo.penalty.messaging.payload;

public record PenaltyAppliedPayload(
        Integer claimId,
        Integer operationId,
        Integer tenantId
) {
}
