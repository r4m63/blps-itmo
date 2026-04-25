package blps.itmo.platform.events.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponsePayload {
    private Long claimId;
    private Long tenantId;
    private boolean agree;
    private String comment;
}
