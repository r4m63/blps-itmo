package blps.itmo.claim.kafka.payload;

import java.math.BigDecimal;

public record ClaimCreatedPayload(
        Integer claimId,
        Integer landlordId,
        Integer tenantId,
        String title,
        String description,
        BigDecimal claimedAmount,
        String currency
) {
}
