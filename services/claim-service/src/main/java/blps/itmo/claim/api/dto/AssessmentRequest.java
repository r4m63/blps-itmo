package blps.itmo.claim.api.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record AssessmentRequest(
        boolean penaltyGrounds,
        @PositiveOrZero BigDecimal assessmentAmount,
        String assessmentNotes
) {
}
