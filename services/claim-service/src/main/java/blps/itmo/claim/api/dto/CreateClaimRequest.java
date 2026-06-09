package blps.itmo.claim.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateClaimRequest(
        @NotNull Integer tenantId,
        @NotBlank String title,
        @NotBlank String description,
        @NotNull @PositiveOrZero BigDecimal claimedAmount,
        String currency
) {
}
