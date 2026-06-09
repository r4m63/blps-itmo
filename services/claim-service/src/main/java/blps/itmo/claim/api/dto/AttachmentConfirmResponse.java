package blps.itmo.claim.api.dto;

import java.time.Instant;

public record AttachmentConfirmResponse(
        Integer attachmentId,
        boolean uploaded,
        Instant confirmedAt
) {
}
