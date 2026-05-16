package blps.itmo.claim.service;

import blps.itmo.claim.api.dto.AdditionalInfoRequest;
import blps.itmo.claim.api.dto.AssessmentRequest;
import blps.itmo.claim.api.dto.CreateClaimRequest;
import blps.itmo.claim.api.dto.CreateMessageRequest;
import blps.itmo.claim.api.dto.IntakeDecisionRequest;
import blps.itmo.claim.api.dto.SupportDecisionRequest;
import blps.itmo.claim.api.dto.TenantResponseRequest;
import blps.itmo.claim.domain.Claim;
import blps.itmo.claim.domain.ClaimMessage;
import blps.itmo.claim.domain.ClaimStatus;
import blps.itmo.claim.domain.ClaimStatusHistory;
import blps.itmo.claim.domain.CommentType;
import blps.itmo.claim.kafka.EventType;
import blps.itmo.claim.kafka.outboxevent.OutboxService;
import blps.itmo.claim.kafka.config.TopicNames;
import blps.itmo.claim.kafka.payload.ClaimCreatedPayload;
import blps.itmo.claim.kafka.payload.PenaltyApplicationRequestedPayload;
import blps.itmo.claim.repository.ClaimMessageRepository;
import blps.itmo.claim.repository.ClaimRepository;
import blps.itmo.claim.repository.ClaimStatusHistoryRepository;
import blps.itmo.claim.saga.PenaltyApplicationSaga;
import blps.itmo.claim.saga.SagaInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final ClaimMessageRepository messageRepository;
    private final ClaimStatusHistoryRepository historyRepository;
    private final OutboxService outboxService;
    private final PenaltyApplicationSaga penaltyApplicationSaga;

    public Claim getById(Integer id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found: " + id));
    }

    public List<Claim> listForUser(Integer userId) {
        return claimRepository.findByLandlordIdOrTenantId(userId, userId);
    }

    public List<Claim> listByStatus(ClaimStatus status) {
        return claimRepository.findByStatus(status);
    }

    public List<ClaimMessage> listMessages(Integer claimId) {
        getById(claimId);
        return messageRepository.findByClaimIdOrderByCreatedAtAsc(claimId);
    }

    public List<ClaimStatusHistory> listHistory(Integer claimId) {
        getById(claimId);
        return historyRepository.findByClaimIdOrderByCreatedAtAsc(claimId);
    }

    @Transactional
    public Claim create(Integer landlordId, CreateClaimRequest req) {
        if (landlordId.equals(req.tenantId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "landlord and tenant must differ");
        }
        Claim claim = new Claim();
        claim.setLandlordId(landlordId);
        claim.setTenantId(req.tenantId());
        claim.setTitle(req.title());
        claim.setDescription(req.description());
        claim.setClaimedAmount(req.claimedAmount());
        if (req.currency() != null && !req.currency().isBlank()) {
            claim.setCurrency(req.currency());
        }
        claim.setStatus(ClaimStatus.SUBMITTED);
        Claim saved = claimRepository.save(claim);
        recordHistory(saved.getId(), null, ClaimStatus.SUBMITTED, landlordId, "claim submitted");
        outboxService.enqueue(
            "claim",
            saved.getId().toString(),
            EventType.CLAIM_CREATED,
            TopicNames.CLAIM_EVENTS,
            new ClaimCreatedPayload(
                saved.getId(),
                saved.getLandlordId(),
                saved.getTenantId(),
                saved.getTitle(),
                saved.getDescription(),
                saved.getClaimedAmount(),
                saved.getCurrency()
            )
        );
        return saved;
    }

    @Transactional
    public Claim startIntake(Integer claimId, Integer adminId) {
        Claim claim = getById(claimId);
        transition(claim, ClaimStatus.SUBMITTED, ClaimStatus.INTAKE_REVIEW, adminId, "intake started");
        claim.setAdminReviewerId(adminId);
        return claimRepository.save(claim);
    }

    @Transactional
    public Claim intakeDecision(Integer claimId, Integer adminId, IntakeDecisionRequest req) {
        Claim claim = getById(claimId);
        ensureStatus(claim, ClaimStatus.INTAKE_REVIEW);
        ClaimStatus target = req.requestAdditionalInfo() ? ClaimStatus.NEED_ADDITIONAL_INFO : ClaimStatus.UNDER_ASSESSMENT;
        transition(claim, ClaimStatus.INTAKE_REVIEW, target, adminId, req.note());
        claim.setAdminReviewerId(adminId);
        Claim saved = claimRepository.save(claim);
        if (req.requestAdditionalInfo()) {
            saveMessage(saved.getId(), adminId, CommentType.ADDITIONAL_INFO_REQUEST,
                    req.note() == null ? "additional info requested" : req.note());
        }
        return saved;
    }

    @Transactional
    public Claim provideAdditionalInfo(Integer claimId, Integer landlordId, AdditionalInfoRequest req) {
        Claim claim = getById(claimId);
        if (!claim.getLandlordId().equals(landlordId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only landlord may provide additional info");
        }
        ensureStatus(claim, ClaimStatus.NEED_ADDITIONAL_INFO);
        transition(claim, ClaimStatus.NEED_ADDITIONAL_INFO, ClaimStatus.INTAKE_REVIEW, landlordId, "additional info provided");
        Claim saved = claimRepository.save(claim);
        saveMessage(saved.getId(), landlordId, CommentType.ADDITIONAL_INFO_REPLY, req.body());
        return saved;
    }

    @Transactional
    public Claim assess(Integer claimId, Integer adminId, AssessmentRequest req) {
        Claim claim = getById(claimId);
        ensureStatus(claim, ClaimStatus.UNDER_ASSESSMENT);
        claim.setAdminReviewerId(adminId);
        claim.setAssessmentAmount(req.assessmentAmount());
        claim.setAssessmentNotes(req.assessmentNotes());

        if (req.penaltyGrounds()) {
            transition(claim, ClaimStatus.UNDER_ASSESSMENT, ClaimStatus.AWAITING_TENANT_RESPONSE, adminId, "penalty grounds confirmed");
        } else {
            Instant now = Instant.now();
            claim.setDecidedAt(now);
            claim.setClosedAt(now);
            claim.setResolutionNote(req.assessmentNotes());
            transition(claim, ClaimStatus.UNDER_ASSESSMENT, ClaimStatus.CLOSED_NO_PENALTY, adminId, "no penalty grounds");
        }
        Claim saved = claimRepository.save(claim);
        if (req.assessmentNotes() != null && !req.assessmentNotes().isBlank()) {
            saveMessage(saved.getId(), adminId, CommentType.ADMIN_NOTE, req.assessmentNotes());
        }
        return saved;
    }

    @Transactional
    public Claim tenantResponse(Integer claimId, Integer tenantId, TenantResponseRequest req) {
        Claim claim = getById(claimId);
        if (!claim.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only tenant may respond");
        }
        ensureStatus(claim, ClaimStatus.AWAITING_TENANT_RESPONSE);
        transition(claim, ClaimStatus.AWAITING_TENANT_RESPONSE, ClaimStatus.SUPPORT_REVIEW, tenantId, "tenant responded");
        Claim saved = claimRepository.save(claim);
        saveMessage(saved.getId(), tenantId, CommentType.TENANT_RESPONSE, req.body());
        return saved;
    }

    @Transactional
    public Claim supportDecision(Integer claimId, Integer adminId, SupportDecisionRequest req) {
        Claim claim = getById(claimId);
        ensureStatus(claim, ClaimStatus.SUPPORT_REVIEW);
        if (req.applyPenalty() && req.penaltyAmount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "penaltyAmount is required when applyPenalty=true");
        }
        claim.setAdminReviewerId(adminId);
        claim.setResolutionNote(req.resolutionNote());
        Instant now = Instant.now();
        claim.setDecidedAt(now);
        if (req.applyPenalty()) {
            claim.setClosedAt(null);
            transition(claim, ClaimStatus.SUPPORT_REVIEW, ClaimStatus.PENALTY_PROCESSING, adminId, req.resolutionNote());
        } else {
            claim.setClosedAt(now);
            transition(claim, ClaimStatus.SUPPORT_REVIEW, ClaimStatus.CLOSED_NO_PENALTY, adminId, req.resolutionNote());
        }
        Claim saved = claimRepository.save(claim);
        if (req.applyPenalty()) {
            SagaInstance saga = penaltyApplicationSaga.start(saved.getId());
            saved.setCurrentSagaId(saga.getSagaId());
            saved = claimRepository.save(saved);
            outboxService.enqueue(
                    "claim",
                    saved.getId().toString(),
                    EventType.PENALTY_APPLICATION_REQUESTED,
                    TopicNames.PENALTY_EVENTS,
                    new PenaltyApplicationRequestedPayload(
                            saved.getId(),
                            saved.getTenantId(),
                            saved.getLandlordId(),
                            adminId,
                    req.penaltyAmount(),
                    req.penaltyCurrency() == null || req.penaltyCurrency().isBlank()
                        ? saved.getCurrency()
                        : req.penaltyCurrency(),
                            req.simulateFailure()
                    ),
                    saga.getSagaId()
            );
        }
        if (req.resolutionNote() != null && !req.resolutionNote().isBlank()) {
            saveMessage(saved.getId(), adminId, CommentType.ADMIN_NOTE, req.resolutionNote());
        }
        return saved;
    }

    @Transactional
    public ClaimMessage addMessage(Integer claimId, Integer userId, CreateMessageRequest req) {
        getById(claimId);
        return saveMessage(claimId, userId, req.messageType(), req.body());
    }

    @Transactional
    public void markPenaltyApplied(Integer claimId, Integer actorId) {
        Claim claim = getById(claimId);
        if (claim.getStatus() != ClaimStatus.PENALTY_PROCESSING) {
            return;
        }
        claim.setStatus(ClaimStatus.PENALTY_APPLIED);
        claim.setClosedAt(Instant.now());
        recordHistory(claim.getId(), ClaimStatus.PENALTY_PROCESSING, ClaimStatus.PENALTY_APPLIED, actorId, "penalty applied");
        claimRepository.save(claim);
    }

    @Transactional
    public void markPenaltyFailed(Integer claimId, String reason) {
        Claim claim = getById(claimId);
        if (claim.getStatus() != ClaimStatus.PENALTY_PROCESSING) {
            return;
        }
        claim.setStatus(ClaimStatus.PENALTY_PROCESSING_FAILED);
        claim.setClosedAt(null);
        recordHistory(claim.getId(), ClaimStatus.PENALTY_PROCESSING, ClaimStatus.PENALTY_PROCESSING_FAILED, null, reason);
        claimRepository.save(claim);
    }

    @Transactional
    public void closeClaimsForDeactivatedUser(Integer userId, String reason) {
        List<Claim> claims = claimRepository.findByStatusNotInAndLandlordIdOrTenantId(
                List.of(ClaimStatus.PENALTY_APPLIED, ClaimStatus.CLOSED_NO_PENALTY),
                userId,
                userId
        );
        for (Claim claim : claims) {
            ClaimStatus from = claim.getStatus();
            claim.setStatus(ClaimStatus.CLOSED_NO_PENALTY);
            claim.setClosedAt(Instant.now());
            recordHistory(claim.getId(), from, ClaimStatus.CLOSED_NO_PENALTY, null, reason);
            claimRepository.save(claim);
        }
    }

    @Transactional
    public int expireTenantResponses(Instant threshold) {
        List<Claim> claims = claimRepository.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            ClaimStatus.AWAITING_TENANT_RESPONSE,
            threshold
        );
        for (Claim claim : claims) {
            transition(claim, ClaimStatus.AWAITING_TENANT_RESPONSE, ClaimStatus.SUPPORT_REVIEW, null, "tenant response expired");
            claimRepository.save(claim);
            outboxService.enqueue(
                    "claim",
                    claim.getId().toString(),
                    EventType.TENANT_RESPONSE_EXPIRED,
                    TopicNames.CLAIM_EVENTS,
                    new blps.itmo.claim.kafka.payload.TenantResponseExpiredPayload(claim.getId())
            );
        }
        return claims.size();
    }

    private void ensureStatus(Claim claim, ClaimStatus expected) {
        if (claim.getStatus() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "claim is in %s, expected %s".formatted(claim.getStatus(), expected));
        }
    }

    private void transition(Claim claim, ClaimStatus from, ClaimStatus to, Integer actorId, String note) {
        claim.setStatus(to);
        recordHistory(claim.getId(), from, to, actorId, note);
    }

    private void recordHistory(Integer claimId, ClaimStatus from, ClaimStatus to, Integer actorId, String note) {
        ClaimStatusHistory h = new ClaimStatusHistory();
        h.setClaimId(claimId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setActorId(actorId);
        h.setNote(note);
        historyRepository.save(h);
    }

    private ClaimMessage saveMessage(Integer claimId, Integer userId, CommentType type, String body) {
        ClaimMessage m = new ClaimMessage();
        m.setClaimId(claimId);
        m.setUserId(userId);
        m.setMessageType(type);
        m.setBody(body);
        return messageRepository.save(m);
    }
}
