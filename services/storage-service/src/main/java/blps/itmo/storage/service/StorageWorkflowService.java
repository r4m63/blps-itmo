package blps.itmo.storage.service;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import blps.itmo.platform.events.EventEnvelope;
import blps.itmo.platform.events.EventType;
import blps.itmo.platform.events.payload.AttachmentBindingFailedPayload;
import blps.itmo.platform.events.payload.AttachmentBindingRequestedPayload;
import blps.itmo.platform.events.payload.AttachmentBoundPayload;
import blps.itmo.platform.events.payload.AttachmentConfirmedPayload;
import blps.itmo.platform.events.payload.AttachmentInitializedPayload;
import blps.itmo.platform.persistence.OutboxService;
import blps.itmo.platform.persistence.ProcessedMessageService;
import blps.itmo.storage.controller.AttachmentController.AttachmentResponse;
import blps.itmo.storage.controller.AttachmentController.InitAttachmentRequest;
import blps.itmo.storage.domain.Attachment;
import blps.itmo.storage.domain.AttachmentStatus;
import blps.itmo.storage.repository.AttachmentRepository;

@Service
public class StorageWorkflowService {

    private static final String CLAIM_CONSUMER = "storage-service-claim-events";

    private final AttachmentRepository attachmentRepository;
    private final ProcessedMessageService processedMessageService;
    private final OutboxService outboxService;
    private final String bucketName;
    private final String publicUrl;
    private final boolean verifyMinioObject;
    private final long bindingTimeoutMinutes;

    public StorageWorkflowService(AttachmentRepository attachmentRepository,
            ProcessedMessageService processedMessageService,
            OutboxService outboxService,
            @Value("${app.storage.bucket-name:claim-attachments}") String bucketName,
            @Value("${app.storage.public-url:http://localhost:9000}") String publicUrl,
            @Value("${app.storage.verify-minio-object:false}") boolean verifyMinioObject,
            @Value("${app.storage.binding-timeout-minutes:30}") long bindingTimeoutMinutes) {
        this.attachmentRepository = attachmentRepository;
        this.processedMessageService = processedMessageService;
        this.outboxService = outboxService;
        this.bucketName = bucketName;
        this.publicUrl = publicUrl;
        this.verifyMinioObject = verifyMinioObject;
        this.bindingTimeoutMinutes = bindingTimeoutMinutes;
    }

    @Transactional
    public AttachmentResponse init(Long ownerUserId, InitAttachmentRequest request) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("Missing attachment owner");
        }
        OffsetDateTime now = OffsetDateTime.now();
        String objectKey = ownerUserId + "/" + UUID.randomUUID() + "-" + sanitize(request.originalFilename());
        Attachment attachment = attachmentRepository.save(Attachment.builder()
                .ownerUserId(ownerUserId)
                .bucketName(bucketName)
                .objectKey(objectKey)
                .originalFilename(request.originalFilename())
                .contentType(request.contentType())
                .status(AttachmentStatus.INITIALIZED)
                .createdAt(now)
                .updatedAt(now)
                .build());
        outboxService.record(
                EventType.ATTACHMENT_INITIALIZED,
                "ATTACHMENT",
                attachment.getId(),
                "attachment-" + attachment.getId(),
                "attachment-upload-" + attachment.getId(),
                ownerUserId,
                AttachmentInitializedPayload.builder()
                        .attachmentId(attachment.getId())
                        .ownerUserId(ownerUserId)
                        .objectKey(objectKey)
                        .originalFilename(request.originalFilename())
                        .build());
        return toResponse(attachment);
    }

    @Transactional
    public AttachmentResponse confirm(Long attachmentId, Long actorUserId, String objectKey) {
        Attachment attachment = requireAttachment(attachmentId);
        if (actorUserId != null && !attachment.getOwnerUserId().equals(actorUserId)) {
            throw new IllegalArgumentException("Attachment does not belong to current user");
        }
        if (objectKey != null && !objectKey.isBlank() && !attachment.getObjectKey().equals(objectKey)) {
            throw new IllegalArgumentException("Object key does not match initialized attachment");
        }
        if (verifyMinioObject && !objectExists(attachment)) {
            throw new IllegalStateException("Object is not visible in MinIO: " + attachment.getObjectKey());
        }
        attachment.setStatus(AttachmentStatus.CONFIRMED);
        attachment.setUpdatedAt(OffsetDateTime.now());
        attachment.setFailureReason(null);
        attachmentRepository.save(attachment);
        outboxService.record(
                EventType.ATTACHMENT_CONFIRMED,
                "ATTACHMENT",
                attachment.getId(),
                "attachment-" + attachment.getId(),
                "attachment-upload-" + attachment.getId(),
                attachment.getOwnerUserId(),
                AttachmentConfirmedPayload.builder()
                        .attachmentId(attachment.getId())
                        .ownerUserId(attachment.getOwnerUserId())
                        .objectKey(attachment.getObjectKey())
                        .build());
        return toResponse(attachment);
    }

    @Transactional(readOnly = true)
    public AttachmentResponse get(Long attachmentId) {
        return toResponse(requireAttachment(attachmentId));
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listByOwner(Long ownerUserId) {
        return attachmentRepository.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void handleBindingRequested(EventEnvelope envelope, AttachmentBindingRequestedPayload payload) {
        if (processedMessageService.isProcessed(envelope.getEventId(), CLAIM_CONSUMER)) {
            return;
        }
        List<Attachment> attachments = attachmentRepository.findByIdIn(payload.getAttachmentIds());
        boolean allFound = attachments.size() == new HashSet<>(payload.getAttachmentIds()).size();
        boolean allConfirmedAndOwned = attachments.stream().allMatch(attachment ->
                attachment.getStatus() == AttachmentStatus.CONFIRMED
                        && attachment.getOwnerUserId().equals(payload.getLandlordId()));
        if (!allFound || !allConfirmedAndOwned) {
            for (Attachment attachment : attachments) {
                attachment.setStatus(AttachmentStatus.BINDING_FAILED);
                attachment.setFailureReason("Attachment binding failed: missing, unconfirmed, or owned by another user");
                attachment.setUpdatedAt(OffsetDateTime.now());
                attachmentRepository.save(attachment);
            }
            outboxService.record(
                    EventType.ATTACHMENT_BINDING_FAILED,
                    "CLAIM",
                    payload.getClaimId(),
                    envelope.getCorrelationId(),
                    "attachment-binding-" + payload.getClaimId(),
                    payload.getLandlordId(),
                    AttachmentBindingFailedPayload.builder()
                            .claimId(payload.getClaimId())
                            .attachmentIds(payload.getAttachmentIds())
                            .reason("Missing, unconfirmed, or foreign attachment")
                            .build());
            processedMessageService.markProcessed(envelope.getEventId(), CLAIM_CONSUMER, envelope.getCorrelationId());
            return;
        }
        for (Attachment attachment : attachments) {
            attachment.setStatus(AttachmentStatus.BOUND);
            attachment.setClaimId(payload.getClaimId());
            attachment.setFailureReason(null);
            attachment.setUpdatedAt(OffsetDateTime.now());
            attachmentRepository.save(attachment);
        }
        outboxService.record(
                EventType.ATTACHMENT_BOUND,
                "CLAIM",
                payload.getClaimId(),
                envelope.getCorrelationId(),
                "attachment-binding-" + payload.getClaimId(),
                payload.getLandlordId(),
                AttachmentBoundPayload.builder()
                        .claimId(payload.getClaimId())
                        .attachmentIds(payload.getAttachmentIds())
                        .build());
        processedMessageService.markProcessed(envelope.getEventId(), CLAIM_CONSUMER, envelope.getCorrelationId());
    }

    @Scheduled(fixedDelayString = "${app.storage.reconciliation-poll-interval-ms:60000}")
    @Transactional
    public void failStaleBindingRequests() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(bindingTimeoutMinutes);
        for (Attachment attachment : attachmentRepository.findByStatusAndUpdatedAtBefore(
                AttachmentStatus.BINDING_REQUESTED, threshold)) {
            attachment.setStatus(AttachmentStatus.BINDING_FAILED);
            attachment.setFailureReason("Attachment binding timed out");
            attachment.setUpdatedAt(OffsetDateTime.now());
            attachmentRepository.save(attachment);
        }
    }

    private Attachment requireAttachment(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + attachmentId));
    }

    private AttachmentResponse toResponse(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getOwnerUserId(),
                attachment.getClaimId(),
                attachment.getBucketName(),
                attachment.getObjectKey(),
                publicUrl + "/" + attachment.getBucketName() + "/" + attachment.getObjectKey(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getStatus().name(),
                attachment.getFailureReason(),
                attachment.getCreatedAt(),
                attachment.getUpdatedAt());
    }

    private String sanitize(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private boolean objectExists(Attachment attachment) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(
                    publicUrl + "/" + attachment.getBucketName() + "/" + attachment.getObjectKey()).toURL().openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            int status = connection.getResponseCode();
            return status >= 200 && status < 400;
        } catch (Exception e) {
            return false;
        }
    }
}
