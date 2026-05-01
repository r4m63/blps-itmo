package blps.itmo.platform.events.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimSupportReviewExpiredPayload {
    private Long claimId;
    private String reason;
}
