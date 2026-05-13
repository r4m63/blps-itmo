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
import blps.itmo.claim.repository.ClaimMessageRepository;
import blps.itmo.claim.repository.ClaimRepository;
import blps.itmo.claim.repository.ClaimStatusHistoryRepository;
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
        claim.setAdminReviewerId(adminId);
        claim.setResolutionNote(req.resolutionNote());
        Instant now = Instant.now();
        claim.setDecidedAt(now);
        claim.setClosedAt(now);

        ClaimStatus target = req.applyPenalty() ? ClaimStatus.PENALTY_APPLIED : ClaimStatus.CLOSED_NO_PENALTY;
        transition(claim, ClaimStatus.SUPPORT_REVIEW, target, adminId, req.resolutionNote());
        Claim saved = claimRepository.save(claim);
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
