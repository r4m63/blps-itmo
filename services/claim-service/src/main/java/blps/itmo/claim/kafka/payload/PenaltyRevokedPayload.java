package blps.itmo.claim.kafka.payload;

public record PenaltyRevokedPayload(
        Integer claimId,
        Integer operationId,
        Integer tenantId,
        boolean wasApplied
) {
}
