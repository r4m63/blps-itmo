package blps.itmo.claim.api.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record SupportDecisionRequest(
        boolean applyPenalty,
        @PositiveOrZero BigDecimal penaltyAmount,
        String penaltyCurrency,
        boolean simulateFailure,
        String resolutionNote
) {
}
