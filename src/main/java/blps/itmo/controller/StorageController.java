package blps.itmo.controller;

import java.time.OffsetDateTime;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import blps.itmo.dto.AttachmentConfirmRequest;
import blps.itmo.dto.AttachmentConfirmResponse;
import blps.itmo.dto.AttachmentInitRequest;
import blps.itmo.dto.AttachmentInitResponse;
import blps.itmo.dto.PresignRequest;
import blps.itmo.dto.PresignResponse;
import blps.itmo.security.AppUserPrincipal;
import blps.itmo.service.MinioService;
import jakarta.validation.Valid;

/**
 * Операции с объектным хранилищем (MinIO).
 */
@RestController
@RequestMapping("/api/storage")
@Validated
@PreAuthorize("hasAuthority(T(blps.itmo.security.Privileges).STORAGE_UPLOAD)")
public class StorageController {

    private final MinioService minioService;

    public StorageController(MinioService minioService) {
        this.minioService = minioService;
    }

    @PostMapping("/presign")
    public PresignResponse presign(@Valid @RequestBody PresignRequest request) {
        String objectKey = minioService.generateObjectKey(request.getFileName());
        String url = minioService.presignPutUrl(objectKey, request.getContentType());
        return PresignResponse.builder()
                .objectKey(objectKey)
                .uploadUrl(url)
                .expiresAt(OffsetDateTime.now().plus(minioService.getPresignTtl()))
                .build();
    }

    @PostMapping("/attachments/init")
    public AttachmentInitResponse initAttachment(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody AttachmentInitRequest request) {
        var result = minioService.initAttachment(
                request.getFileName(),
                request.getContentType(),
                request.getPurpose(),
                principal.getUserId());
        return AttachmentInitResponse.builder()
                .attachmentId(result.attachmentId())
                .objectKey(result.objectKey())
                .uploadUrl(result.uploadUrl())
                .expiresAt(result.expiresAt())
                .build();
    }

    @PostMapping("/attachments/confirm")
    public AttachmentConfirmResponse confirmAttachment(@Valid @RequestBody AttachmentConfirmRequest request) {
        var attachment = minioService.confirmUpload(request.getObjectKey());
        return AttachmentConfirmResponse.builder()
                .attachmentId(attachment.getId())
                .objectKey(attachment.getObjectKey())
                .sizeBytes(attachment.getSizeBytes())
                .contentType(attachment.getContentType())
                .confirmedAt(attachment.getConfirmedAt())
                .uploaded(Boolean.TRUE.equals(attachment.getUploaded()))
                .build();
    }
}
