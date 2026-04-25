package blps.itmo.claim.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import blps.itmo.claim.client.AuthClient;
import blps.itmo.claim.controller.ClaimController.AdditionalInfoRequest;
import blps.itmo.claim.controller.ClaimController.ClaimResponse;
import blps.itmo.claim.controller.ClaimController.CreateClaimRequest;
import blps.itmo.claim.controller.ClaimController.SupportDecisionRequest;
import blps.itmo.claim.controller.ClaimController.TenantResponseRequest;
import blps.itmo.claim.domain.Claim;
import blps.itmo.claim.domain.ClaimStatus;
import blps.itmo.claim.domain.ClaimTimelineEntry;
import blps.itmo.claim.repository.ClaimRepository;
import blps.itmo.claim.repository.ClaimTimelineRepository;
import blps.itmo.platform.events.EventEnvelope;
import blps.itmo.platform.events.EventType;
import blps.itmo.platform.events.payload.AdditionalInfoProvidedPayload;
import blps.itmo.platform.events.payload.AssessmentCompletedPayload;
import blps.itmo.platform.events.payload.ClaimCreatedPayload;
import blps.itmo.platform.events.payload.PenaltyApplicationFailedPayload;
import blps.itmo.platform.events.payload.PenaltyApplicationRequestedPayload;
import blps.itmo.platform.events.payload.PenaltyAppliedPayload;
import blps.itmo.platform.events.payload.TenantResponsePayload;
import blps.itmo.platform.events.payload.UserDeactivatedPayload;
import blps.itmo.platform.persistence.OutboxService;
import blps.itmo.platform.persistence.ProcessedMessageService;

@Service
public class ClaimProcessService {

    private static final String ASSESSMENT_CONSUMER = "claim-service-assessment";
    private static final String PENALTY_CONSUMER = "claim-service-penalty";
    private static final String AUTH_CONSUMER = "claim-service-auth";

    private final ClaimRepository claimRepository;
    private final ClaimTimelineRepository claimTimelineRepository;
    private final AuthClient authClient;
    private final OutboxService outboxService;
    private final ProcessedMessageService processedMessageService;

    public ClaimProcessService(ClaimRepository claimRepository,
            ClaimTimelineRepository claimTimelineRepository,
            AuthClient authClient,
            OutboxService outboxService,
            ProcessedMessageService processedMessageService) {
        this.claimRepository = claimRepository;
        this.claimTimelineRepository = claimTimelineRepository;
        this.authClient = authClient;
        this.outboxService = outboxService;
        this.processedMessageService = processedMessageService;
    }

    @Transactional
    public ClaimResponse createClaim(CreateClaimRequest request) {
        authClient.requireUser(request.landlordUserId(), "LANDLORD");
        authClient.requireUser(request.tenantUserId(), "TENANT");
        if (request.landlordUserId().equals(request.tenantUserId())) {
            throw new IllegalArgumentException("Landlord and tenant must be different users");
        }
        String correlationId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        Claim claim = claimRepository.save(Claim.builder()
                .correlationId(correlationId)
                .landlordId(request.landlordUserId())
                .tenantId(request.tenantUserId())
                .status(ClaimStatus.ASSESSMENT_IN_PROGRESS)
                .title(request.title())
                .description(request.description())
                .claimedAmount(request.claimedAmount())
                .currency(request.currency())
                .createdAt(now)
                .updatedAt(now)
                .build());
        addTimeline(claim.getId(), "CLAIM_CREATED", null, claim.getStatus(), request.landlordUserId(),
                "Claim submitted and queued for asynchronous assessment");
        outboxService.record(
                EventType.CLAIM_CREATED,
                "CLAIM",
                claim.getId(),
                claim.getCorrelationId(),
                "claim-lifecycle-" + claim.getId(),
                request.landlordUserId(),
                ClaimCreatedPayload.builder()
                        .claimId(claim.getId())
                        .landlordId(request.landlordUserId())
                        .tenantId(request.tenantUserId())
                        .title(request.title())
                        .description(request.description())
                        .claimedAmount(request.claimedAmount())
                        .currency(request.currency())
                        .build());
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

    @Transactional
    public ClaimResponse provideAdditionalInfo(Long claimId, AdditionalInfoRequest request) {
        Claim claim = requireClaim(claimId);
        if (claim.getStatus() != ClaimStatus.NEED_ADDITIONAL_INFO) {
            throw new IllegalStateException("Claim is not waiting for additional info");
        }
        authClient.requireUser(request.landlordUserId(), "LANDLORD");
        if (!claim.getLandlordId().equals(request.landlordUserId())) {
            throw new IllegalArgumentException("Landlord does not own this claim");
        }
        ClaimStatus from = claim.getStatus();
        claim.setStatus(ClaimStatus.ASSESSMENT_IN_PROGRESS);
        claim.setUpdatedAt(OffsetDateTime.now());
        claimRepository.save(claim);
        addTimeline(claim.getId(), "ADDITIONAL_INFO_PROVIDED", from, claim.getStatus(), request.landlordUserId(),
                request.comment());
        outboxService.record(
                EventType.ADDITIONAL_INFO_PROVIDED,
                "CLAIM",
                claim.getId(),
                claim.getCorrelationId(),
                "claim-lifecycle-" + claim.getId(),
                request.landlordUserId(),
                AdditionalInfoProvidedPayload.builder()
                        .claimId(claim.getId())
                        .landlordId(request.landlordUserId())
                        .comment(request.comment())
                        .claimedAmount(claim.getClaimedAmount())
                        .currency(claim.getCurrency())
                        .build());
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse submitTenantResponse(Long claimId, TenantResponseRequest request) {
        Claim claim = requireClaim(claimId);
        if (claim.getStatus() != ClaimStatus.AWAITING_TENANT_RESPONSE) {
            throw new IllegalStateException("Claim is not waiting for tenant response");
        }
        authClient.requireUser(request.tenantUserId(), "TENANT");
        if (!claim.getTenantId().equals(request.tenantUserId())) {
            throw new IllegalArgumentException("Tenant does not belong to this claim");
        }
        ClaimStatus from = claim.getStatus();
        claim.setStatus(ClaimStatus.SUPPORT_REVIEW);
        claim.setUpdatedAt(OffsetDateTime.now());
        claimRepository.save(claim);
        addTimeline(claim.getId(), "TENANT_RESPONSE_RECEIVED", from, claim.getStatus(), request.tenantUserId(),
                request.comment());
        outboxService.record(
                EventType.TENANT_RESPONSE_RECEIVED,
                "CLAIM",
                claim.getId(),
                claim.getCorrelationId(),
                "claim-lifecycle-" + claim.getId(),
                request.tenantUserId(),
                TenantResponsePayload.builder()
                        .claimId(claim.getId())
                        .tenantId(request.tenantUserId())
                        .agree(request.agree())
                        .comment(request.comment())
                        .build());
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse supportDecision(Long claimId, SupportDecisionRequest request) {
        Claim claim = requireClaim(claimId);
        if (claim.getStatus() != ClaimStatus.SUPPORT_REVIEW) {
            throw new IllegalStateException("Claim is not ready for support decision");
        }
        authClient.requireUser(request.adminUserId(), "ADMIN");
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
            addTimeline(claim.getId(), "PENALTY_APPLICATION_REQUESTED", from, claim.getStatus(), request.adminUserId(),
                    request.note());
            outboxService.record(
                    EventType.PENALTY_APPLICATION_REQUESTED,
                    "CLAIM",
                    claim.getId(),
                    claim.getCorrelationId(),
                    "penalty-application-" + claim.getId(),
                    request.adminUserId(),
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
            addTimeline(claim.getId(), "CLAIM_CLOSED_NO_PENALTY", from, claim.getStatus(), request.adminUserId(),
                    request.note());
            outboxService.record(
                    EventType.CLAIM_CLOSED_NO_PENALTY,
                    "CLAIM",
                    claim.getId(),
                    claim.getCorrelationId(),
                    "claim-lifecycle-" + claim.getId(),
                    request.adminUserId(),
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

    private Claim requireClaim(Long id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + id));
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
                claim.getClosedAt());
    }
}
