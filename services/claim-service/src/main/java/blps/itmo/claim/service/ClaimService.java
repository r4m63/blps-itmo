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
import blps.itmo.claim.kafka.payload.PenaltyApplicationFailedPayload;
import blps.itmo.claim.kafka.payload.PenaltyCountedPayload;
import blps.itmo.claim.process.ClaimProcessActions;
import blps.itmo.claim.process.ClaimProcessService;
import blps.itmo.claim.repository.ClaimMessageRepository;
import blps.itmo.claim.repository.ClaimRepository;
import blps.itmo.claim.repository.ClaimStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final ClaimMessageRepository messageRepository;
    private final ClaimStatusHistoryRepository historyRepository;
    private final ClaimProcessActions actions;
    private final ClaimProcessService processService;

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
        Claim saved = actions.create(
                landlordId,
                req.tenantId(),
                req.title(),
                req.description(),
                req.claimedAmount(),
                req.currency()
        );
        processService.start(saved.getId(), saved.getLandlordId(), saved.getTenantId());
        return saved;
    }

    @Transactional
    public Claim startIntake(Integer claimId, Integer adminId) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("adminId", adminId);
        processService.complete(claimId, ClaimProcessService.TASK_INTAKE_START, adminId, variables);
        return getById(claimId);
    }

    @Transactional
    public Claim intakeDecision(Integer claimId, Integer adminId, IntakeDecisionRequest req) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("adminId", adminId);
        variables.put("requestAdditionalInfo", req.requestAdditionalInfo());
        variables.put("note", req.note());
        processService.complete(claimId, ClaimProcessService.TASK_INTAKE_DECISION, adminId, variables);
        return getById(claimId);
    }

    @Transactional
    public Claim provideAdditionalInfo(Integer claimId, Integer landlordId, AdditionalInfoRequest req) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("landlordId", landlordId);
        variables.put("additionalInfoBody", req.body());
        processService.complete(claimId, ClaimProcessService.TASK_ADDITIONAL_INFO, landlordId, variables);
        return getById(claimId);
    }

    @Transactional
    public Claim assess(Integer claimId, Integer adminId, AssessmentRequest req) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("adminId", adminId);
        variables.put("penaltyGrounds", req.penaltyGrounds());
        variables.put("assessmentAmount", req.assessmentAmount());
        variables.put("assessmentNotes", req.assessmentNotes());
        processService.complete(claimId, ClaimProcessService.TASK_ASSESSMENT, adminId, variables);
        return getById(claimId);
    }

    @Transactional
    public Claim tenantResponse(Integer claimId, Integer tenantId, TenantResponseRequest req) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("tenantId", tenantId);
        variables.put("tenantResponseBody", req.body());
        processService.complete(claimId, ClaimProcessService.TASK_TENANT_RESPONSE, tenantId, variables);
        return getById(claimId);
    }

    @Transactional
    public Claim supportDecision(Integer claimId, Integer adminId, SupportDecisionRequest req) {
        if (req.applyPenalty() && req.penaltyAmount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "penaltyAmount is required when applyPenalty=true");
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("adminId", adminId);
        variables.put("applyPenalty", req.applyPenalty());
        variables.put("penaltyAmount", req.penaltyAmount());
        variables.put("penaltyCurrency", req.penaltyCurrency());
        variables.put("simulateFailure", req.simulateFailure());
        variables.put("resolutionNote", req.resolutionNote());
        processService.complete(claimId, ClaimProcessService.TASK_SUPPORT_DECISION, adminId, variables);
        return getById(claimId);
    }

    @Transactional
    public ClaimMessage addMessage(Integer claimId, Integer userId, CreateMessageRequest req) {
        return actions.addMessage(claimId, userId, req.messageType(), req.body());
    }

    @Transactional
    public void markPenaltyApplied(Integer claimId, Integer actorId) {
        actions.markPenaltyCounted(new PenaltyCountedPayload(claimId, actorId, null, null));
    }

    @Transactional
    public void markPenaltyFailed(Integer claimId, String reason) {
        actions.markPenaltyApplicationFailed(new PenaltyApplicationFailedPayload(claimId, null, reason));
    }

    @Transactional
    public void closeClaimsForDeactivatedUser(Integer userId, String reason) {
        actions.closeClaimsForDeactivatedUser(userId, reason);
    }

    @Transactional
    public int expireTenantResponses(Instant threshold) {
        return 0;
    }
}
