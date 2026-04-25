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
public class AssessmentCompletedPayload {
    private Long claimId;
    private BigDecimal assessmentAmount;
    private String assessmentNotes;
    private boolean penaltyGrounds;
    private boolean requiresAdditionalInfo;
}
