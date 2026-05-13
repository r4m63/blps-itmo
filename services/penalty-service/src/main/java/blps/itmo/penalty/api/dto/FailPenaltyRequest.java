package blps.itmo.penalty.api.dto;

import jakarta.validation.constraints.NotBlank;

public record FailPenaltyRequest(
        @NotBlank String reason
) {
}
