package blps.itmo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AttachmentConfirmRequest {
    @NotBlank
    private String objectKey;
}
