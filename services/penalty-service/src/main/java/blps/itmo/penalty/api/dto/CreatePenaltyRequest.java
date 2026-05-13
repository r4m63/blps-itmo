package blps.itmo.penalty.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreatePenaltyRequest(
        @NotNull Integer claimId,
        @NotNull Integer tenantId,
        @NotNull Integer landlordId,
        @NotNull @PositiveOrZero BigDecimal amount,
        String currency
) {
}
