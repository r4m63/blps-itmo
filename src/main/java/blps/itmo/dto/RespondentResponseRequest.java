package blps.itmo.dto;

import jakarta.validation.constraints.NotBlank;

public record RespondentResponseRequest(@NotBlank String comment) {
}
