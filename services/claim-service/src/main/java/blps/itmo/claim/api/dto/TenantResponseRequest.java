package blps.itmo.claim.api.dto;

import jakarta.validation.constraints.NotBlank;

public record TenantResponseRequest(
        @NotBlank String body
) {
}
