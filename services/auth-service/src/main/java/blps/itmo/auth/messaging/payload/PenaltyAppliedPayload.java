package blps.itmo.auth.messaging.payload;

public record PenaltyAppliedPayload(
        Integer claimId,
        Integer operationId,
        Integer tenantId
) {
}
