package blps.itmo.claim.api.dto;

import blps.itmo.claim.domain.CommentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateMessageRequest(
        @NotNull CommentType messageType,
        @NotBlank String body
) {
}
