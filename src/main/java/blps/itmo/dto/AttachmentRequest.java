package blps.itmo.dto;

import blps.itmo.entity.AttachmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AttachmentRequest(
        @NotNull AttachmentType type,
        @NotBlank String url,
        @NotBlank String uploaderId
) {
}
