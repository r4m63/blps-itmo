package blps.itmo.service;

import blps.itmo.entity.Attachment;
import blps.itmo.entity.AttachmentType;
import blps.itmo.entity.Claim;
import blps.itmo.entity.ClaimStatus;
import blps.itmo.exception.DomainException;
import blps.itmo.repository.AttachmentRepository;
import blps.itmo.repository.ClaimRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final AttachmentRepository attachmentRepository;

    public ClaimService(ClaimRepository claimRepository, AttachmentRepository attachmentRepository) {
        this.claimRepository = claimRepository;
        this.attachmentRepository = attachmentRepository;
    }

    @Transactional
    public Claim createClaim(String initiatorId,
                             String respondentId,
                             BigDecimal amount,
                             String currency,
                             String reason) {
        Claim claim = new Claim(initiatorId, respondentId, amount, currency, reason, OffsetDateTime.now());
        return claimRepository.save(claim);
    }

    @Transactional(readOnly = true)
    public Claim get(Long id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Claim %d not found".formatted(id)));
    }

    @Transactional
    public Claim addAttachment(Long claimId, String uploaderId, AttachmentType type, String url) {
        Claim claim = get(claimId);
        Attachment attachment = new Attachment(claim, uploaderId, type, url, OffsetDateTime.now());
        claim.addAttachment(attachment);
        attachmentRepository.save(attachment);
        return claim;
    }

    @Transactional
    public Claim intakeCheck(Long claimId, boolean enoughData) {
        Claim claim = get(claimId);
        if (claim.getStatus() != ClaimStatus.DATA_REVIEW && claim.getStatus() != ClaimStatus.NEED_ADDITIONAL_DATA) {
            throw new DomainException("Intake check allowed only in DATA_REVIEW or NEED_ADDITIONAL_DATA");
        }
        claim.setEnoughData(enoughData);
        return claim;
    }

    @Transactional
    public Claim provideAdditionalData(Long claimId) {
        Claim claim = get(claimId);
        if (claim.getStatus() != ClaimStatus.NEED_ADDITIONAL_DATA) {
            throw new DomainException("Providing additional data allowed only in NEED_ADDITIONAL_DATA");
        }
        claim.moveToDataReview();
        return claim;
    }

    @Transactional
    public Claim rulesCheck(Long claimId, boolean groundsForPenalty) {
        Claim claim = get(claimId);
        if (claim.getStatus() != ClaimStatus.RISK_REVIEW) {
            throw new DomainException("Rules check allowed only in RISK_REVIEW");
        }
        claim.setGroundsForPenalty(groundsForPenalty, OffsetDateTime.now());
        return claim;
    }

    @Transactional
    public Claim respondentResponse(Long claimId, String comment) {
        Claim claim = get(claimId);
        if (claim.getStatus() != ClaimStatus.WAITING_RESPONDENT) {
            throw new DomainException("Respondent response allowed only in WAITING_RESPONDENT");
        }
        claim.respondentProvided(comment);
        return claim;
    }

    @Transactional
    public Claim markTimeout(Long claimId) {
        Claim claim = get(claimId);
        if (claim.getStatus() != ClaimStatus.WAITING_RESPONDENT) {
            throw new DomainException("Timeout only allowed in WAITING_RESPONDENT");
        }
        claim.markTimeout();
        return claim;
    }

    @Transactional
    public Claim supportDecision(Long claimId, boolean approvePenalty, String comment) {
        Claim claim = get(claimId);
        if (claim.getStatus() != ClaimStatus.SUPPORT_REVIEW) {
            throw new DomainException("Support decision allowed only in SUPPORT_REVIEW");
        }
        claim.supportDecision(approvePenalty, comment);
        return claim;
    }
}
