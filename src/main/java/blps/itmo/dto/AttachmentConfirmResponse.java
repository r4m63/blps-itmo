package blps.itmo.dto;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AttachmentConfirmResponse {
    private Long attachmentId;
    private String objectKey;
    private Long sizeBytes;
    private String contentType;
    private OffsetDateTime confirmedAt;
    private boolean uploaded;
}
