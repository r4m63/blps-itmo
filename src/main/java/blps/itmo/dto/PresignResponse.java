package blps.itmo.dto;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PresignResponse {
    private String objectKey;
    private String uploadUrl;
    private OffsetDateTime expiresAt;
}
