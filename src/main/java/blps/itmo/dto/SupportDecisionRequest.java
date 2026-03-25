package blps.itmo.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class SupportDecisionRequest {
    @NotNull
    private Long adminId;
    private boolean applyPenalty;
    private BigDecimal penaltyAmount;
    private String penaltyCurrency = "USD";
    private String note;
}
