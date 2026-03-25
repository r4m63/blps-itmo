package blps.itmo.dto;

import blps.itmo.entity.AttachmentPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AttachmentInitRequest {
    @NotBlank
    private String fileName;

    @NotBlank
    private String contentType;

    @NotNull
    private Long uploadedBy;

    private AttachmentPurpose purpose = AttachmentPurpose.DAMAGE_EVIDENCE;
}
