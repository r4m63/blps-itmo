package blps.itmo.penalty.messaging.payload;

import java.math.BigDecimal;

public record PenaltyApplicationRequestedPayload(
        Integer claimId,
        Integer tenantId,
        Integer landlordId,
        Integer requestedBy,
        BigDecimal amount,
        String currency,
        boolean simulateFailure
) {
}
