package blps.itmo.platform.events.payload;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentBindingFailedPayload {
    private Long claimId;
    private List<Long> attachmentIds;
    private String reason;
}
