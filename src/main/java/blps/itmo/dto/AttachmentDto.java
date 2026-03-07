package blps.itmo.dto;

import java.time.OffsetDateTime;

import blps.itmo.entity.Attachment;
import blps.itmo.entity.AttachmentType;

public record AttachmentDto(
        Long id,
        AttachmentType type,
        String url,
        String uploaderId,
        OffsetDateTime createdAt) {
    public static AttachmentDto from(Attachment a) {
        return new AttachmentDto(a.getId(), a.getType(), a.getUrl(), a.getUploaderId(), a.getCreatedAt());
    }
}
