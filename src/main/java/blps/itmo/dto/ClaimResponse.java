package blps.itmo.dto;

import blps.itmo.entity.ClaimStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClaimResponse {
    private Long id;
    private Long landlordId;
    private Long tenantId;
    private ClaimStatus status;
    private String title;
    private String description;
    private BigDecimal claimedAmount;
    private String currency;
    private OffsetDateTime createdAt;
}
