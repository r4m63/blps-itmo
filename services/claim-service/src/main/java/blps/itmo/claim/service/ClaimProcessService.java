package blps.itmo.claim.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import blps.itmo.claim.client.AuthClient;
import blps.itmo.claim.controller.ClaimController.ClaimAttachmentResponse;
import blps.itmo.claim.controller.ClaimController.ClaimProcessStatusResponse;
import blps.itmo.claim.controller.ClaimController.AdditionalInfoRequest;
import blps.itmo.claim.controller.ClaimController.ClaimResponse;
import blps.itmo.claim.controller.ClaimController.CreateClaimRequest;
import blps.itmo.claim.controller.ClaimController.SupportDecisionRequest;
import blps.itmo.claim.controller.ClaimController.TenantResponseRequest;
import blps.itmo.claim.domain.Claim;
import blps.itmo.claim.domain.ClaimAttachment;
import blps.itmo.claim.domain.ClaimAttachmentStatus;
import blps.itmo.claim.domain.ClaimStatus;
import blps.itmo.claim.domain.ClaimTimelineEntry;
import blps.itmo.claim.repository.ClaimAttachmentRepository;
import blps.itmo.claim.repository.ClaimRepository;
import blps.itmo.claim.repository.ClaimTimelineRepository;
import blps.itmo.platform.events.EventEnvelope;
import blps.itmo.platform.events.EventType;
import blps.itmo.platform.events.payload.AdditionalInfoProvidedPayload;
import blps.itmo.platform.events.payload.AssessmentCompletedPayload;
import blps.itmo.platform.events.payload.AssessmentFailedPayload;
import blps.itmo.platform.events.payload.AttachmentBindingFailedPayload;
import blps.itmo.platform.events.payload.AttachmentBindingRequestedPayload;
import blps.itmo.platform.events.payload.AttachmentBoundPayload;
import blps.itmo.platform.events.payload.ClaimCreatedPayload;
import blps.itmo.platform.events.payload.PenaltyApplicationFailedPayload;
import blps.itmo.platform.events.payload.PenaltyApplicationRequestedPayload;
import blps.itmo.platform.events.payload.PenaltyAppliedPayload;
import blps.itmo.platform.events.payload.TenantResponsePayload;
import blps.itmo.platform.events.payload.UserDeactivatedPayload;
import blps.itmo.platform.outbox.OutboxService;
import blps.itmo.platform.outbox.ProcessedMessageService;

@Service
public class ClaimProcessService {

    private static final String ASSESSMENT_CONSUMER = "claim-service-assessment";
    private static final String PENALTY_CONSUMER = "claim-service-penalty";
    private static final String AUTH_CONSUMER = "claim-service-auth";
    private static final String STORAGE_CONSUMER = "claim-service-storage";

    private final ClaimRepository claimRepository;
    private final ClaimAttachmentRepository claimAttachmentRepository;
    private final ClaimTimelineRepository claimTimelineRepository;
    private final AuthClient authClient;
    private final OutboxService outboxService;
    private final ProcessedMessageService processedMessageService;
    private final long tenantResponseTimeoutMinutes;

    public ClaimProcessService(ClaimRepository claimRepository,
            ClaimAttachmentRepository claimAttachmentRepository,
            ClaimTimelineRepository claimTimelineRepository,
            AuthClient authClient,
            OutboxService outboxService,
            ProcessedMessageService processedMessageService,
            @Value("${app.claim.tenant-response-timeout-minutes:60}") long tenantResponseTimeoutMinutes) {
        this.claimRepository = claimRepository;
        this.claimAttachmentRepository = claimAttachmentRepository;
        this.claimTimelineRepository = claimTimelineRepository;
        this.authClient = authClient;
        this.outboxService = outboxService;
        this.processedMessageService = processedMessageService;
        this.tenantResponseTimeoutMinutes = tenantResponseTimeoutMinutes;
    }

    @Transactional
    public ClaimResponse createClaim(CreateClaimRequest request, Long actorUserId) {
        Long landlordId = actorOrBody(actorUserId, request.landlordUserId(), "landlord user");
        authClient.requireUser(landlordId, "LANDLORD");
        authClient.requireUser(request.tenantUserId(), "TENANT");
        if (landlordId.equals(request.tenantUserId())) {
            throw new IllegalArgumentException("Landlord and tenant must be different users");
        }
        String correlationId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        Claim claim = claimRepository.save(Claim.builder()
                .correlationId(correlationId)
                .landlordId(landlordId)
                .tenantId(request.tenantUserId())
                .status(ClaimStatus.ASSESSMENT_IN_PROGRESS)
                .title(request.title())
                .description(request.description())
                .claimedAmount(request.claimedAmount())
                .currency(request.currency())
                .createdAt(now)
                .updatedAt(now)
                .build());
        addTimeline(claim.getId(), "CLAIM_CREATED", null, claim.getStatus(), landlordId,
                "Claim submitted and queued for asynchronous assessment");
        outboxService.record(
                EventType.CLAIM_CREATED,
                "CLAIM",
                claim.getId(),
                claim.getCorrelationId(),
                "claim-lifecycle-" + claim.getId(),
                landlordId,
                ClaimCreatedPayload.builder()
                        .claimId(claim.getId())
                        .landlordId(landlordId)
                        .tenantId(request.tenantUserId())
                        .title(request.title())
                        .description(request.description())
                        .claimedAmount(request.claimedAmount())
                        .currency(request.currency())
                        .build());
        List<Long> attachmentIds = request.attachmentIds() == null ? List.of() : request.attachmentIds();
        if (!attachmentIds.isEmpty()) {
            for (Long attachmentId : attachmentIds) {
                claimAttachmentRepository.save(ClaimAttachment.builder()
                        .claimId(claim.getId())
                        .attachmentId(attachmentId)
                        .status(ClaimAttachmentStatus.BINDING_REQUESTED)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
            }
            outboxService.record(
                    EventType.ATTACHMENT_BINDING_REQUESTED,
                    "CLAIM",
                    claim.getId(),
                    claim.getCorrelationId(),
                    "attachment-binding-" + claim.getId(),
                    landlordId,
                    AttachmentBindingRequestedPayload.builder()
                            .claimId(claim.getId())
                            .landlordId(landlordId)
                            .attachmentIds(attachmentIds)
                            .build());
        }
        return toResponse(claim);
    }

    @Transactional(readOnly = true)
    public ClaimResponse getClaim(Long id) {
        return toResponse(requireClaim(id));
    }

    @Transactional(readOnly = true)
    public List<ClaimTimelineEntry> getTimeline(Long id) {
        requireClaim(id);
        return claimTimelineRepository.findByClaimIdOrderByCreatedAtAsc(id);
    }

    @Transactional(readOnly = true)
    public List<ClaimAttachmentResponse> getAttachments(Long id) {
        requireClaim(id);
        return attachmentResponses(id);
    }

    @Transactional(readOnly = true)
    public ClaimProcessStatusResponse getProcessStatus(Long id) {
        Claim claim = requireClaim(id);
        return new ClaimProcessStatusResponse(
                claim.getId(),
                claim.getStatus().name(),
                claim.getCorrelationId(),
                isTerminal(claim.getStatus()),
                attachmentResponses(id));
    }

    @Transactional
    public ClaimResponse provideAdditionalInfo(Long claimId, AdditionalInfoRequest request, Long actorUserId) {
        Claim claim = requireClaim(claimId);
        if (claim.getStatus() != ClaimStatus.NEED_ADDITIONAL_INFO) {
            throw new IllegalStateException("Claim is not waiting for additional info");
        }
        Long landlordId = actorOrBody(actorUserId, request.landlordUserId(), "landlord user");
        authClient.requireUser(landlordId, "LANDLORD");
        if (!claim.getLandlordId().equals(landlordId)) {
            throw new IllegalArgumentException("Landlord does not own this claim");
        }
        ClaimStatus from = claim.getStatus();
        claim.setStatus(ClaimStatus.ASSESSMENT_IN_PROGRESS);
        claim.setUpdatedAt(OffsetDateTime.now());
        claimRepository.save(claim);
        addTimeline(claim.getId(), "ADDITIONAL_INFO_PROVIDED", from, claim.getStatus(), landlordId,
                request.comment());
        outboxService.record(
                EventType.ADDITIONAL_INFO_PROVIDED,
                "CLAIM",
                claim.getId(),
                claim.getCorrelationId(),
                "claim-lifecycle-" + claim.getId(),
                landlordId,
                AdditionalInfoProvidedPayload.builder()
                        .claimId(claim.getId())
                        .landlordId(landlordId)
                        .comment(request.comment())
                        .claimedAmount(claim.getClaimedAmount())
                        .currency(claim.getCurrency())
                        .build());
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse submitTenantResponse(Long claimId, TenantResponseRequest request, Long actorUserId) {
        Claim claim = requireClaim(claimId);
        if (claim.getStatus() != ClaimStatus.AWAITING_TENANT_RESPONSE) {
            throw new IllegalStateException("Claim is not waiting for tenant response");
        }
        Long tenantId = actorOrBody(actorUserId, request.tenantUserId(), "tenant user");
        authClient.requireUser(tenantId, "TENANT");
        if (!claim.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Tenant does not belong to this claim");
        }
        ClaimStatus from = claim.getStatus();
        claim.setStatus(ClaimStatus.SUPPORT_REVIEW);
        claim.setUpdatedAt(OffsetDateTime.now());
        claimRepository.save(claim);
        addTimeline(claim.getId(), "TENANT_RESPONSE_RECEIVED", from, claim.getStatus(), tenantId,
                request.comment());
        outboxService.record(
                EventType.TENANT_RESPONSE_RECEIVED,
                "CLAIM",
                claim.getId(),
                claim.getCorrelationId(),
                "claim-lifecycle-" + claim.getId(),
                tenantId,
                TenantResponsePayload.builder()
                        .claimId(claim.getId())
                        .tenantId(tenantId)
                        .agree(request.agree())
                        .comment(request.comment())
                        .build());
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse supportDecision(Long claimId, SupportDecisionRequest request, Long actorUserId) {
        Claim claim = requireClaim(claimId);
        if (claim.getStatus() != ClaimStatus.SUPPORT_REVIEW) {
            throw new IllegalStateException("Claim is not ready for support decision");
        }
        Long adminId = actorOrBody(actorUserId, request.adminUserId(), "admin user");
        authClient.requireUser(adminId, "ADMIN");
        ClaimStatus from = claim.getStatus();
        claim.setUpdatedAt(OffsetDateTime.now());
        claim.setResolutionNote(request.note());
        if (request.applyPenalty()) {
            if (request.penaltyAmount() == null || request.penaltyAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Positive penaltyAmount is required when applyPenalty=true");
            }
            claim.setStatus(ClaimStatus.PENALTY_PROCESSING);
            claim.setPenaltyAmount(request.penaltyAmount());
            claim.setPenaltyCurrency(
                    request.penaltyCurrency() == null || request.penaltyCurrency().isBlank()
                            ? claim.getCurrency()
                            : request.penaltyCurrency());
            claimRepository.save(claim);
            addTimeline(claim.getId(), "PENALTY_APPLICATION_REQUESTED", from, claim.getStatus(), adminId,
                    request.note());
            outboxService.record(
                    EventType.PENALTY_APPLICATION_REQUESTED,
                    "CLAIM",
                    claim.getId(),
                    claim.getCorrelationId(),
                    "penalty-application-" + claim.getId(),
                    adminId,
                    PenaltyApplicationRequestedPayload.builder()
                            .claimId(claim.getId())
                            .tenantId(claim.getTenantId())
                            .penaltyAmount(request.penaltyAmount())
                            .penaltyCurrency(request.penaltyCurrency())
                            .note(request.note())
                            .simulateFailure(request.simulateFailure())
                            .build());
        } else {
            claim.setStatus(ClaimStatus.CLOSED_NO_PENALTY);
            claim.setPenaltyAmount(null);
            claim.setClosedAt(OffsetDateTime.now());
            claimRepository.save(claim);
            addTimeline(claim.getId(), "CLAIM_CLOSED_NO_PENALTY", from, claim.getStatus(), adminId,
                    request.note());
            outboxService.record(
                    EventType.CLAIM_CLOSED_NO_PENALTY,
                    "CLAIM",
                    claim.getId(),
                    claim.getCorrelationId(),
                    "claim-lifecycle-" + claim.getId(),
                    adminId,
                    java.util.Map.of(
                            "claimId", claim.getId(),
                            "note", request.note() == null ? "" : request.note()));
        }
        return toResponse(claim);
    }

    @Transactional
    public void handleAssessmentCompleted(EventEnvelope envelope, AssessmentCompletedPayload payload) {
        if (processedMessageService.isProcessed(envelope.getEventId(), ASSESSMENT_CONSUMER)) {
            return;
        }
        Claim claim = requireClaim(payload.getClaimId());
        ClaimStatus from = claim.getStatus();
        claim.setAssessmentAmount(payload.getAssessmentAmount());
        claim.setAssessmentNotes(payload.getAssessmentNotes());
        claim.setUpdatedAt(OffsetDateTime.now());
        if (payload.isRequiresAdditionalInfo()) {
            claim.setStatus(ClaimStatus.NEED_ADDITIONAL_INFO);
        } else if (payload.isPenaltyGrounds()) {
            claim.setStatus(ClaimStatus.AWAITING_TENANT_RESPONSE);
        } else {
            claim.setStatus(ClaimStatus.CLOSED_NO_PENALTY);
            claim.setClosedAt(OffsetDateTime.now());
        }
        claimRepository.save(claim);
        addTimeline(claim.getId(), envelope.getEventType().name(), from, claim.getStatus(), envelope.getActorId(),
                payload.getAssessmentNotes());
        processedMessageService.markProcessed(envelope.getEventId(), ASSESSMENT_CONSUMER,
                envelope.getCorrelationId());
    }

    @Transactional
    public void handleAssessmentFailed(EventEnvelope envelope, AssessmentFailedPayload payload) {
        if (processedMessageService.isProcessed(envelope.getEventId(), ASSESSMENT_CONSUMER)) {
            return;
        }
        Claim claim = requireClaim(payload.getClaimId());
        ClaimStatus from = claim.getStatus();
        claim.setStatus(ClaimStatus.ASSESSMENT_FAILED);
        claim.setAssessmentNotes(payload.getReason());
        claim.setUpdatedAt(OffsetDateTime.now());
        claimRepository.save(claim);
        addTimeline(claim.getId(), envelope.getEventType().name(), from, claim.getStatus(), envelope.getActorId(),
                payload.getReason());
        processedMessageService.markProcessed(envelope.getEventId(), ASSESSMENT_CONSUMER,
                envelope.getCorrelationId());
    }

    @Transactional
    public void handlePenaltyApplied(EventEnvelope envelope, PenaltyAppliedPayload payload) {
        if (processedMessageService.isProcessed(envelope.getEventId(), PENALTY_CONSUMER)) {
            return;
        }
        Claim claim = requireClaim(payload.getClaimId());
        ClaimStatus from = claim.getStatus();
        claim.setStatus(ClaimStatus.PENALTY_APPLIED);
        claim.setPenaltyAmount(payload.getPenaltyAmount());
        claim.setPenaltyCurrency(payload.getPenaltyCurrency());
        claim.setClosedAt(OffsetDateTime.now());
        claim.setUpdatedAt(OffsetDateTime.now());
        claimRepository.save(claim);
        addTimeline(claim.getId(), envelope.getEventType().name(), from, claim.getStatus(), envelope.getActorId(),
                "Penalty applied successfully");
        processedMessageService.markProcessed(envelope.getEventId(), PENALTY_CONSUMER,
                envelope.getCorrelationId());
    }

    @Transactional
    public void handlePenaltyFailed(EventEnvelope envelope, PenaltyApplicationFailedPayload payload) {
        if (processedMessageService.isProcessed(envelope.getEventId(), PENALTY_CONSUMER)) {
            return;
        }
        Claim claim = requireClaim(payload.getClaimId());
        ClaimStatus from = claim.getStatus();
        claim.setStatus(ClaimStatus.PENALTY_PROCESSING_FAILED);
        claim.setUpdatedAt(OffsetDateTime.now());
        claimRepository.save(claim);
        addTimeline(claim.getId(), envelope.getEventType().name(), from, claim.getStatus(), envelope.getActorId(),
                payload.getReason());
        processedMessageService.markProcessed(envelope.getEventId(), PENALTY_CONSUMER,
                envelope.getCorrelationId());
    }

    @Transactional
    public void handleUserDeactivated(EventEnvelope envelope, UserDeactivatedPayload payload) {
        if (processedMessageService.isProcessed(envelope.getEventId(), AUTH_CONSUMER)) {
            return;
        }
        List<ClaimStatus> closedStatuses = List.of(
                ClaimStatus.CLOSED_NO_PENALTY,
                ClaimStatus.PENALTY_APPLIED);
        List<Claim> claims = claimRepository.findByLandlordIdOrTenantId(payload.getUserId(), payload.getUserId());
        for (Claim claim : claims) {
            if (closedStatuses.contains(claim.getStatus())) {
                continue;
            }
            ClaimStatus from = claim.getStatus();
            claim.setStatus(ClaimStatus.CLOSED_NO_PENALTY);
            claim.setClosedAt(OffsetDateTime.now());
            claim.setUpdatedAt(OffsetDateTime.now());
            claim.setResolutionNote("Closed after user deactivation");
            claimRepository.save(claim);
            addTimeline(claim.getId(), envelope.getEventType().name(), from, claim.getStatus(), payload.getUserId(),
                    payload.getReason());
        }
        processedMessageService.markProcessed(envelope.getEventId(), AUTH_CONSUMER, envelope.getCorrelationId());
    }

    @Transactional
    public void handleAttachmentBound(EventEnvelope envelope, AttachmentBoundPayload payload) {
        if (processedMessageService.isProcessed(envelope.getEventId(), STORAGE_CONSUMER)) {
            return;
        }
        List<ClaimAttachment> attachments = claimAttachmentRepository.findByClaimIdAndAttachmentIdIn(
                payload.getClaimId(), payload.getAttachmentIds());
        for (ClaimAttachment attachment : attachments) {
            attachment.setStatus(ClaimAttachmentStatus.BOUND);
            attachment.setFailureReason(null);
            attachment.setUpdatedAt(OffsetDateTime.now());
            claimAttachmentRepository.save(attachment);
        }
        addTimeline(payload.getClaimId(), envelope.getEventType().name(), null, requireClaim(payload.getClaimId()).getStatus(),
                envelope.getActorId(), "Attachments bound: " + payload.getAttachmentIds());
        processedMessageService.markProcessed(envelope.getEventId(), STORAGE_CONSUMER, envelope.getCorrelationId());
    }

    @Transactional
    public void handleAttachmentBindingFailed(EventEnvelope envelope, AttachmentBindingFailedPayload payload) {
        if (processedMessageService.isProcessed(envelope.getEventId(), STORAGE_CONSUMER)) {
            return;
        }
        List<ClaimAttachment> attachments = claimAttachmentRepository.findByClaimIdAndAttachmentIdIn(
                payload.getClaimId(), payload.getAttachmentIds());
        for (ClaimAttachment attachment : attachments) {
            attachment.setStatus(ClaimAttachmentStatus.BINDING_FAILED);
            attachment.setFailureReason(payload.getReason());
            attachment.setUpdatedAt(OffsetDateTime.now());
            claimAttachmentRepository.save(attachment);
        }
        Claim claim = requireClaim(payload.getClaimId());
        addTimeline(claim.getId(), envelope.getEventType().name(), claim.getStatus(), claim.getStatus(),
                envelope.getActorId(), payload.getReason());
        processedMessageService.markProcessed(envelope.getEventId(), STORAGE_CONSUMER, envelope.getCorrelationId());
    }

    @Transactional
    public ClaimResponse repairReassess(Long claimId, Long actorUserId, String note) {
        Claim claim = requireClaim(claimId);
        if (!List.of(ClaimStatus.ASSESSMENT_FAILED, ClaimStatus.MANUAL_REVIEW_REQUIRED, ClaimStatus.NEED_ADDITIONAL_INFO)
                .contains(claim.getStatus())) {
            throw new IllegalStateException("Claim is not eligible for reassessment repair");
        }
        Long adminId = actorUserId == null ? null : authClient.requireUser(actorUserId, "ADMIN").getId();
        ClaimStatus from = claim.getStatus();
        claim.setStatus(ClaimStatus.ASSESSMENT_IN_PROGRESS);
        claim.setUpdatedAt(OffsetDateTime.now());
        claimRepository.save(claim);
        addTimeline(claim.getId(), "REPAIR_REASSESSMENT_REQUESTED", from, claim.getStatus(), adminId, note);
        outboxService.record(
                EventType.ADDITIONAL_INFO_PROVIDED,
                "CLAIM",
                claim.getId(),
                claim.getCorrelationId(),
                "claim-lifecycle-" + claim.getId(),
                adminId,
                AdditionalInfoProvidedPayload.builder()
                        .claimId(claim.getId())
                        .landlordId(claim.getLandlordId())
                        .comment(note == null ? "Manual reassessment repair" : note)
                        .claimedAmount(claim.getClaimedAmount())
                        .currency(claim.getCurrency())
                        .build());
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse repairClose(Long claimId, Long actorUserId, String note) {
        Claim claim = requireClaim(claimId);
        if (isTerminal(claim.getStatus())) {
            throw new IllegalStateException("Claim is already terminal");
        }
        Long adminId = actorUserId == null ? null : authClient.requireUser(actorUserId, "ADMIN").getId();
        ClaimStatus from = claim.getStatus();
        claim.setStatus(ClaimStatus.CLOSED_NO_PENALTY);
        claim.setResolutionNote(note == null ? "Closed manually by repair action" : note);
        claim.setClosedAt(OffsetDateTime.now());
        claim.setUpdatedAt(OffsetDateTime.now());
        claimRepository.save(claim);
        addTimeline(claim.getId(), "REPAIR_CLAIM_CLOSED", from, claim.getStatus(), adminId, claim.getResolutionNote());
        return toResponse(claim);
    }

    @Scheduled(fixedDelayString = "${app.claim.timeout-poll-interval-ms:60000}")
    @Transactional
    public void expireTenantResponses() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(tenantResponseTimeoutMinutes);
        for (Claim claim : claimRepository.findByStatusAndUpdatedAtBefore(ClaimStatus.AWAITING_TENANT_RESPONSE, threshold)) {
            ClaimStatus from = claim.getStatus();
            claim.setStatus(ClaimStatus.SUPPORT_REVIEW);
            claim.setUpdatedAt(OffsetDateTime.now());
            claim.setResolutionNote("Tenant response SLA expired; moved to support review");
            claimRepository.save(claim);
            addTimeline(claim.getId(), EventType.TENANT_RESPONSE_EXPIRED.name(), from, claim.getStatus(), null,
                    claim.getResolutionNote());
            outboxService.record(
                    EventType.TENANT_RESPONSE_EXPIRED,
                    "CLAIM",
                    claim.getId(),
                    claim.getCorrelationId(),
                    "claim-lifecycle-" + claim.getId(),
                    null,
                    java.util.Map.of("claimId", claim.getId(), "reason", claim.getResolutionNote()));
        }
    }

    private Claim requireClaim(Long id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + id));
    }

    private Long actorOrBody(Long actorUserId, Long bodyUserId, String label) {
        Long value = actorUserId == null ? bodyUserId : actorUserId;
        if (value == null) {
            throw new IllegalArgumentException("Missing " + label);
        }
        return value;
    }

    private boolean isTerminal(ClaimStatus status) {
        return status == ClaimStatus.CLOSED_NO_PENALTY || status == ClaimStatus.PENALTY_APPLIED;
    }

    private List<ClaimAttachmentResponse> attachmentResponses(Long claimId) {
        return claimAttachmentRepository.findByClaimIdOrderByCreatedAtAsc(claimId).stream()
                .map(attachment -> new ClaimAttachmentResponse(
                        attachment.getAttachmentId(),
                        attachment.getStatus().name(),
                        attachment.getFailureReason()))
                .toList();
    }

    private void addTimeline(Long claimId,
            String eventType,
            ClaimStatus fromStatus,
            ClaimStatus toStatus,
            Long actorId,
            String note) {
        claimTimelineRepository.save(ClaimTimelineEntry.builder()
                .claimId(claimId)
                .eventType(eventType)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .actorId(actorId)
                .note(note)
                .createdAt(OffsetDateTime.now())
                .build());
    }

    private ClaimResponse toResponse(Claim claim) {
        return new ClaimResponse(
                claim.getId(),
                claim.getCorrelationId(),
                claim.getLandlordId(),
                claim.getTenantId(),
                claim.getStatus().name(),
                claim.getTitle(),
                claim.getDescription(),
                claim.getClaimedAmount(),
                claim.getCurrency(),
                claim.getAssessmentAmount(),
                claim.getAssessmentNotes(),
                claim.getPenaltyAmount(),
                claim.getPenaltyCurrency(),
                claim.getResolutionNote(),
                claim.getCreatedAt(),
                claim.getUpdatedAt(),
                claim.getClosedAt(),
                attachmentResponses(claim.getId()));
    }
}
