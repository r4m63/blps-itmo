package blps.itmo.dto;

import jakarta.validation.constraints.NotNull;

public record IntakeCheckRequest(@NotNull Boolean enoughData) {
}
