package blps.itmo.dto;

import blps.itmo.entity.Attachment;
import blps.itmo.entity.AttachmentType;

import java.time.OffsetDateTime;

public record AttachmentDto(
        Long id,
        AttachmentType type,
        String url,
        String uploaderId,
        OffsetDateTime createdAt
) {
    public static AttachmentDto from(Attachment a) {
        return new AttachmentDto(a.getId(), a.getType(), a.getUrl(), a.getUploaderId(), a.getCreatedAt());
    }
}
