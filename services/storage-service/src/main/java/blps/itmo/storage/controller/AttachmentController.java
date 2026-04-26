package blps.itmo.storage.controller;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import blps.itmo.storage.service.StorageWorkflowService;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final StorageWorkflowService storageWorkflowService;

    public AttachmentController(StorageWorkflowService storageWorkflowService) {
        this.storageWorkflowService = storageWorkflowService;
    }

    @PostMapping("/init")
    public AttachmentResponse init(
            @RequestHeader(value = "X-User-Id", required = false) Long actorUserId,
            @RequestBody InitAttachmentRequest request) {
        return storageWorkflowService.init(actorUserId == null ? request.ownerUserId() : actorUserId, request);
    }

    @PostMapping("/{id}/confirm")
    public AttachmentResponse confirm(
            @RequestHeader(value = "X-User-Id", required = false) Long actorUserId,
            @PathVariable Long id,
            @RequestBody(required = false) ConfirmAttachmentRequest request) {
        return storageWorkflowService.confirm(id, actorUserId, request == null ? null : request.objectKey());
    }

    @GetMapping("/{id}")
    public AttachmentResponse get(@PathVariable Long id) {
        return storageWorkflowService.get(id);
    }

    @GetMapping
    public List<AttachmentResponse> listMine(@RequestHeader("X-User-Id") Long actorUserId) {
        return storageWorkflowService.listByOwner(actorUserId);
    }

    public record InitAttachmentRequest(
            Long ownerUserId,
            @NotBlank String originalFilename,
            String contentType) {
    }

    public record ConfirmAttachmentRequest(String objectKey) {
    }

    public record AttachmentResponse(
            Long id,
            Long ownerUserId,
            Long claimId,
            String bucketName,
            String objectKey,
            String uploadUrl,
            String originalFilename,
            String contentType,
            String status,
            String failureReason,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }
}
