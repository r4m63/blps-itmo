package blps.itmo.claim.kafka.payload;

public record PenaltyAppliedPayload(
        Integer claimId,
        Integer operationId,
        Integer tenantId
) {
}
