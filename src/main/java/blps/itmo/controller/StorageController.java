package blps.itmo.controller;

import blps.itmo.dto.AttachmentConfirmRequest;
import blps.itmo.dto.AttachmentConfirmResponse;
import blps.itmo.dto.AttachmentInitRequest;
import blps.itmo.dto.AttachmentInitResponse;
import blps.itmo.dto.PresignRequest;
import blps.itmo.dto.PresignResponse;
import blps.itmo.entity.User;
import blps.itmo.exception.ResourceNotFoundException;
import blps.itmo.repository.UserRepository;
import blps.itmo.service.MinioService;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/storage")
@Validated
public class StorageController {

    private final MinioService minioService;
    private final UserRepository userRepository;

    public StorageController(MinioService minioService, UserRepository userRepository) {
        this.minioService = minioService;
        this.userRepository = userRepository;
    }

    @PostMapping("/presign")
    public ResponseEntity<PresignResponse> presign(@Valid @RequestBody PresignRequest request) {
        String objectKey = minioService.generateObjectKey(request.getFileName());
        String url = minioService.presignPutUrl(objectKey, request.getContentType());
        return ResponseEntity.ok(PresignResponse.builder()
                .objectKey(objectKey)
                .uploadUrl(url)
                .expiresAt(OffsetDateTime.now().plus(minioService.getPresignTtl()))
                .build());
    }

    @PostMapping("/attachments/init")
    public ResponseEntity<AttachmentInitResponse> initAttachment(@Valid @RequestBody AttachmentInitRequest request) {
        User uploader = userRepository.findById(request.getUploadedBy())
                .orElseThrow(() -> ResourceNotFoundException.of(User.class, "id", request.getUploadedBy()));
        var result = minioService.initAttachment(
                request.getFileName(),
                request.getContentType(),
                request.getPurpose(),
                uploader
        );
        return ResponseEntity.ok(AttachmentInitResponse.builder()
                .attachmentId(result.attachmentId())
                .objectKey(result.objectKey())
                .uploadUrl(result.uploadUrl())
                .expiresAt(result.expiresAt())
                .build());
    }

    @PostMapping("/attachments/confirm")
    public ResponseEntity<AttachmentConfirmResponse> confirmAttachment(@Valid @RequestBody AttachmentConfirmRequest request) {
        var attachment = minioService.confirmUpload(request.getObjectKey());
        return ResponseEntity.ok(AttachmentConfirmResponse.builder()
                .attachmentId(attachment.getId())
                .objectKey(attachment.getObjectKey())
                .sizeBytes(attachment.getSizeBytes())
                .contentType(attachment.getContentType())
                .confirmedAt(attachment.getConfirmedAt())
                .uploaded(Boolean.TRUE.equals(attachment.getUploaded()))
                .build());
    }
}
