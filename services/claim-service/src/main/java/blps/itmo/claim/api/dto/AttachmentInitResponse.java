package blps.itmo.claim.api.dto;

public record AttachmentInitResponse(
        Integer attachmentId,
        String objectKey,
        String uploadUrl
) {
}
