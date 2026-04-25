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
public class ClaimCreatedPayload {
    private Long claimId;
    private Long landlordId;
    private Long tenantId;
    private String title;
    private String description;
    private BigDecimal claimedAmount;
    private String currency;
}
