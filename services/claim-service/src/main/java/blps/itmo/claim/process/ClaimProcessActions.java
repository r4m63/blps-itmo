package blps.itmo.claim.process;

import blps.itmo.claim.domain.Claim;
import blps.itmo.claim.domain.ClaimMessage;
import blps.itmo.claim.domain.ClaimStatus;
import blps.itmo.claim.domain.ClaimStatusHistory;
import blps.itmo.claim.domain.CommentType;
import blps.itmo.claim.kafka.EventType;
import blps.itmo.claim.kafka.config.TopicNames;
import blps.itmo.claim.kafka.outboxevent.OutboxService;
import blps.itmo.claim.kafka.payload.ClaimCreatedPayload;
import blps.itmo.claim.kafka.payload.PenaltyApplicationFailedPayload;
import blps.itmo.claim.kafka.payload.PenaltyApplicationRequestedPayload;
import blps.itmo.claim.kafka.payload.PenaltyCountedPayload;
import blps.itmo.claim.kafka.payload.PenaltyRevokeCommandPayload;
import blps.itmo.claim.kafka.payload.PenaltyRevokedPayload;
import blps.itmo.claim.repository.ClaimMessageRepository;
import blps.itmo.claim.repository.ClaimRepository;
import blps.itmo.claim.repository.ClaimStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClaimProcessActions {

    private final ClaimRepository claimRepository;
    private final ClaimMessageRepository messageRepository;
    private final ClaimStatusHistoryRepository historyRepository;
    private final OutboxService outboxService;

    @Transactional
    public Claim create(Integer landlordId, Integer tenantId, String title, String description,
                        BigDecimal claimedAmount, String currency) {
        if (landlordId.equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "landlord and tenant must differ");
        }
        Claim claim = new Claim();
        claim.setLandlordId(landlordId);
        claim.setTenantId(tenantId);
        claim.setTitle(title);
        claim.setDescription(description);
        claim.setClaimedAmount(claimedAmount);
        if (currency != null && !currency.isBlank()) {
            claim.setCurrency(currency);
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
    public void recordIntakeStarted(Integer claimId, Integer adminId) {
        Claim claim = getById(claimId);
        transition(claim, ClaimStatus.SUBMITTED, ClaimStatus.INTAKE_REVIEW, adminId, "intake started");
        claim.setAdminReviewerId(adminId);
        claimRepository.save(claim);
    }

    @Transactional
    public void recordIntakeDecision(Integer claimId, Integer adminId, boolean requestAdditionalInfo, String note) {
        Claim claim = getById(claimId);
        ensureStatus(claim, ClaimStatus.INTAKE_REVIEW);
        ClaimStatus target = requestAdditionalInfo ? ClaimStatus.NEED_ADDITIONAL_INFO : ClaimStatus.UNDER_ASSESSMENT;
        transition(claim, ClaimStatus.INTAKE_REVIEW, target, adminId, note);
        claim.setAdminReviewerId(adminId);
        claimRepository.save(claim);
        if (requestAdditionalInfo) {
            saveMessage(claim.getId(), adminId, CommentType.ADDITIONAL_INFO_REQUEST,
                    note == null ? "additional info requested" : note);
        }
    }

    @Transactional
    public void recordAdditionalInfo(Integer claimId, Integer landlordId, String body) {
        Claim claim = getById(claimId);
        if (!claim.getLandlordId().equals(landlordId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only landlord may provide additional info");
        }
        ensureStatus(claim, ClaimStatus.NEED_ADDITIONAL_INFO);
        transition(claim, ClaimStatus.NEED_ADDITIONAL_INFO, ClaimStatus.INTAKE_REVIEW, landlordId, "additional info provided");
        claimRepository.save(claim);
        saveMessage(claim.getId(), landlordId, CommentType.ADDITIONAL_INFO_REPLY, body);
    }

    @Transactional
    public void recordAssessment(Integer claimId, Integer adminId, boolean penaltyGrounds,
                                 BigDecimal assessmentAmount, String assessmentNotes) {
        Claim claim = getById(claimId);
        ensureStatus(claim, ClaimStatus.UNDER_ASSESSMENT);
        claim.setAdminReviewerId(adminId);
        claim.setAssessmentAmount(assessmentAmount);
        claim.setAssessmentNotes(assessmentNotes);
        if (penaltyGrounds) {
            transition(claim, ClaimStatus.UNDER_ASSESSMENT, ClaimStatus.AWAITING_TENANT_RESPONSE,
                    adminId, "penalty grounds confirmed");
        } else {
            Instant now = Instant.now();
            claim.setDecidedAt(now);
            claim.setClosedAt(now);
            claim.setResolutionNote(assessmentNotes);
            transition(claim, ClaimStatus.UNDER_ASSESSMENT, ClaimStatus.CLOSED_NO_PENALTY,
                    adminId, "no penalty grounds");
        }
        claimRepository.save(claim);
        if (assessmentNotes != null && !assessmentNotes.isBlank()) {
            saveMessage(claim.getId(), adminId, CommentType.ADMIN_NOTE, assessmentNotes);
        }
    }

    @Transactional
    public void recordTenantResponse(Integer claimId, Integer tenantId, String body) {
        Claim claim = getById(claimId);
        if (!claim.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only tenant may respond");
        }
        ensureStatus(claim, ClaimStatus.AWAITING_TENANT_RESPONSE);
        transition(claim, ClaimStatus.AWAITING_TENANT_RESPONSE, ClaimStatus.SUPPORT_REVIEW, tenantId, "tenant responded");
        claimRepository.save(claim);
        saveMessage(claim.getId(), tenantId, CommentType.TENANT_RESPONSE, body);
    }

    @Transactional
    public void expireTenantResponse(Integer claimId) {
        Claim claim = getById(claimId);
        if (claim.getStatus() != ClaimStatus.AWAITING_TENANT_RESPONSE) {
            return;
        }
        transition(claim, ClaimStatus.AWAITING_TENANT_RESPONSE, ClaimStatus.SUPPORT_REVIEW,
                null, "tenant response expired");
        claimRepository.save(claim);
        outboxService.enqueue(
                "claim",
                claim.getId().toString(),
                EventType.TENANT_RESPONSE_EXPIRED,
                TopicNames.CLAIM_EVENTS,
                new blps.itmo.claim.kafka.payload.TenantResponseExpiredPayload(claim.getId())
        );
    }

    @Transactional
    public void recordSupportDecision(Integer claimId, Integer adminId, boolean applyPenalty, String resolutionNote) {
        Claim claim = getById(claimId);
        ensureStatus(claim, ClaimStatus.SUPPORT_REVIEW);
        claim.setAdminReviewerId(adminId);
        claim.setResolutionNote(resolutionNote);
        Instant now = Instant.now();
        claim.setDecidedAt(now);
        if (applyPenalty) {
            claim.setClosedAt(null);
            transition(claim, ClaimStatus.SUPPORT_REVIEW, ClaimStatus.PENALTY_PROCESSING, adminId, resolutionNote);
        } else {
            claim.setClosedAt(now);
            transition(claim, ClaimStatus.SUPPORT_REVIEW, ClaimStatus.CLOSED_NO_PENALTY, adminId, resolutionNote);
        }
        claimRepository.save(claim);
        if (resolutionNote != null && !resolutionNote.isBlank()) {
            saveMessage(claim.getId(), adminId, CommentType.ADMIN_NOTE, resolutionNote);
        }
    }

    @Transactional
    public UUID startPenaltySaga(Integer claimId) {
        Claim claim = getById(claimId);
        if (claim.getStatus() != ClaimStatus.PENALTY_PROCESSING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "claim is in %s, expected %s".formatted(claim.getStatus(), ClaimStatus.PENALTY_PROCESSING));
        }
        UUID sagaId = UUID.randomUUID();
        claim.setCurrentSagaId(sagaId);
        claimRepository.save(claim);
        return sagaId;
    }

    @Transactional
    public void enqueuePenaltyApplication(Integer claimId, Integer adminId, BigDecimal penaltyAmount,
                                          String penaltyCurrency, boolean simulateFailure, UUID sagaId) {
        Claim claim = getById(claimId);
        outboxService.enqueue(
                "claim",
                claim.getId().toString(),
                EventType.PENALTY_APPLICATION_REQUESTED,
                TopicNames.PENALTY_EVENTS,
                new PenaltyApplicationRequestedPayload(
                        claim.getId(),
                        claim.getTenantId(),
                        claim.getLandlordId(),
                        adminId,
                        penaltyAmount,
                        penaltyCurrency == null || penaltyCurrency.isBlank() ? claim.getCurrency() : penaltyCurrency,
                        simulateFailure
                ),
                sagaId
        );
    }

    @Transactional
    public void markPenaltyApplicationFailed(PenaltyApplicationFailedPayload payload) {
        Claim claim = claimRepository.findById(payload.claimId()).orElse(null);
        if (claim != null && claim.getStatus() == ClaimStatus.PENALTY_PROCESSING) {
            claim.setStatus(ClaimStatus.PENALTY_PROCESSING_FAILED);
            claim.setClosedAt(null);
            claimRepository.save(claim);
            recordHistory(claim.getId(), ClaimStatus.PENALTY_PROCESSING, ClaimStatus.PENALTY_PROCESSING_FAILED,
                    null, payload.reason());
        }
    }

    @Transactional
    public void markPenaltyCounted(PenaltyCountedPayload payload) {
        Claim claim = claimRepository.findById(payload.claimId()).orElse(null);
        if (claim != null && claim.getStatus() == ClaimStatus.PENALTY_PROCESSING) {
            Instant now = Instant.now();
            claim.setStatus(ClaimStatus.PENALTY_APPLIED);
            claim.setClosedAt(now);
            claimRepository.save(claim);
            recordHistory(claim.getId(), ClaimStatus.PENALTY_PROCESSING, ClaimStatus.PENALTY_APPLIED,
                    null, "saga completed: penalty applied & counted");
        }
    }

    @Transactional
    public void markPenaltyRevoked(PenaltyRevokedPayload payload) {
        Claim claim = claimRepository.findById(payload.claimId()).orElse(null);
        if (claim != null && claim.getStatus() == ClaimStatus.PENALTY_PROCESSING) {
            ClaimStatus from = claim.getStatus();
            claim.setStatus(ClaimStatus.PENALTY_PROCESSING_FAILED);
            claim.setClosedAt(null);
            claimRepository.save(claim);
            recordHistory(claim.getId(), from, ClaimStatus.PENALTY_PROCESSING_FAILED,
                    null, "saga compensated: penalty revoked");
        }
    }

    @Transactional
    public void enqueuePenaltyRevoke(Integer claimId, UUID sagaId, String reason) {
        outboxService.enqueue(
                "saga",
                sagaId.toString(),
                EventType.PENALTY_REVOKE_COMMAND,
                TopicNames.PENALTY_EVENTS,
                new PenaltyRevokeCommandPayload(claimId, null, reason),
                sagaId
        );
    }

    @Transactional
    public ClaimMessage addMessage(Integer claimId, Integer userId, CommentType type, String body) {
        getById(claimId);
        return saveMessage(claimId, userId, type, body);
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

    private Claim getById(Integer id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found: " + id));
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
