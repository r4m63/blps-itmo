package blps.itmo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class CreateClaimRequest {

    @NotNull
    private Long landlordId;

    @NotNull
    private Long tenantId;

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal claimedAmount;

    @NotBlank
    private String currency = "USD";
}
