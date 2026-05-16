package blps.itmo.claim.kafka.payload;

public record PenaltyCountedPayload(
        Integer claimId,
        Integer tenantId,
        Integer operationId,
        Integer newPenaltyCount
) {
}
