package blps.itmo.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.*;

public record CreateClaimRequest(
        @NotBlank String initiatorId,
        @NotBlank String respondentId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @NotBlank @Size(max = 8) String currency,
        @NotBlank @Size(max = 4096) String reason,
        List<AttachmentRequest> attachments) {
}
