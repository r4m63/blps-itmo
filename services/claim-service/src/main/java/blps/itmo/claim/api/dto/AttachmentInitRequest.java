package blps.itmo.claim.api.dto;

import blps.itmo.claim.domain.AttachmentPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AttachmentInitRequest(
        @NotBlank String fileName,
        String contentType,
        Long sizeBytes,
        @NotNull AttachmentPurpose purpose
) {
}
