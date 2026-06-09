package blps.itmo.penalty.kafka.payload;

public record PenaltyApplicationFailedPayload(
        Integer claimId,
        Integer operationId,
        String reason
) {
}
