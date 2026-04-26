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
public class AttachmentBindingRequestedPayload {
    private Long claimId;
    private Long landlordId;
    private List<Long> attachmentIds;
}
