package blps.itmo.claim.messaging.payload;

public record PenaltyAppliedPayload(
        Integer claimId,
        Integer operationId,
        Integer tenantId
) {
}
