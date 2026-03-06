package blps.itmo.dto;

import jakarta.validation.constraints.NotNull;

public record RulesCheckRequest(@NotNull Boolean groundsForPenalty) {
}
