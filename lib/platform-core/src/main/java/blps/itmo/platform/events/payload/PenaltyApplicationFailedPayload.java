package blps.itmo.platform.events.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PenaltyApplicationFailedPayload {
    private Long claimId;
    private Long tenantId;
    private Long operationId;
    private String reason;
}
