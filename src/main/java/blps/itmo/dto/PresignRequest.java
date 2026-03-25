package blps.itmo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PresignRequest {
    @NotBlank
    private String fileName;

    @NotBlank
    private String contentType;
}
