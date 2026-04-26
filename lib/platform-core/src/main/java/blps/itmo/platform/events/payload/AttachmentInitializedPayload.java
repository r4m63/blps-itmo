package blps.itmo.platform.events.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentInitializedPayload {
    private Long attachmentId;
    private Long ownerUserId;
    private String objectKey;
    private String originalFilename;
}
