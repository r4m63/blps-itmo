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
public class AdditionalInfoProvidedPayload {
    private Long claimId;
    private Long landlordId;
    private String comment;
    private BigDecimal claimedAmount;
    private String currency;
}
