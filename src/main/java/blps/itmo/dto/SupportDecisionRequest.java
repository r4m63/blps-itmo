package blps.itmo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SupportDecisionRequest(
        @NotNull Boolean approvePenalty,
        @NotBlank String comment
) {
}
