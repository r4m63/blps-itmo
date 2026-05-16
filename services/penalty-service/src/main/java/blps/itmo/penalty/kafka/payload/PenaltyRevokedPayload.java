package blps.itmo.penalty.kafka.payload;

public record PenaltyRevokedPayload(
        Integer claimId,
        Integer operationId,
        Integer tenantId,
        boolean wasApplied
) {
}
