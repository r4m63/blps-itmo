package blps.itmo.dto;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PresignDownloadResponse {
    private String objectKey;
    private String downloadUrl;
    private OffsetDateTime expiresAt;
}
