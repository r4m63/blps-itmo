package blps.itmo.platform.events.payload;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PenaltyApplicationRequestedPayload {
    private Long claimId;
    private Long tenantId;
    private BigDecimal penaltyAmount;
    private String penaltyCurrency;
    private String note;
    private boolean simulateFailure;
}
