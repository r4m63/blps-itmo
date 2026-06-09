package blps.itmo.auth.kafka.payload;

public record PenaltyAppliedPayload(
        Integer claimId,
        Integer operationId,
        Integer tenantId
) {
}
