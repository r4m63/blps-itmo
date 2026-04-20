package blps.itmo.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import blps.itmo.dto.AdditionalInfoReplyRequest;
import blps.itmo.dto.AssessmentRequest;
import blps.itmo.dto.ClaimResponse;
import blps.itmo.dto.CreateClaimRequest;
import blps.itmo.dto.IntakeDecisionRequest;
import blps.itmo.dto.SupportDecisionRequest;
import blps.itmo.dto.TenantResponseRequest;
import blps.itmo.entity.auth.User;
import blps.itmo.entity.auth.UserRole;
import blps.itmo.entity.business.Claim;
import blps.itmo.entity.business.ClaimMessage;
import blps.itmo.entity.business.ClaimStatus;
import blps.itmo.entity.business.ClaimStatusHistory;
import blps.itmo.entity.business.CommentType;
import blps.itmo.exception.BadRequestException;
import blps.itmo.exception.ConflictException;
import blps.itmo.exception.ResourceNotFoundException;
import blps.itmo.repository.business.ClaimAttachmentRepository;
import blps.itmo.repository.business.ClaimMessageRepository;
import blps.itmo.repository.business.ClaimRepository;
import blps.itmo.repository.business.ClaimStatusHistoryRepository;

@Service
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final AuthUserService authUserService;
    private final ClaimStatusHistoryRepository statusHistoryRepository;
    private final MinioService minioService;
    private final ClaimMessageRepository claimMessageRepository;
    private final ClaimAttachmentRepository claimAttachmentRepository;
    private final TransactionTemplate txTemplate;
    private final TransactionTemplate readOnlyTxTemplate;

    public ClaimService(ClaimRepository claimRepository,
            AuthUserService authUserService,
            ClaimStatusHistoryRepository statusHistoryRepository,
            MinioService minioService,
            ClaimMessageRepository claimMessageRepository,
            ClaimAttachmentRepository claimAttachmentRepository,
            @Qualifier("jtaTransactionTemplate") TransactionTemplate txTemplate,
            @Qualifier("jtaReadOnlyTransactionTemplate") TransactionTemplate readOnlyTxTemplate) {
        this.claimRepository = claimRepository;
        this.authUserService = authUserService;
        this.statusHistoryRepository = statusHistoryRepository;
        this.minioService = minioService;
        this.claimMessageRepository = claimMessageRepository;
        this.claimAttachmentRepository = claimAttachmentRepository;
        this.txTemplate = txTemplate;
        this.readOnlyTxTemplate = readOnlyTxTemplate;
    }

    public ClaimResponse createClaim(Long landlordUserId, CreateClaimRequest request) {
        return txTemplate.execute(status -> {
            User landlord = authUserService.requireUserWithRole(
                    landlordUserId,
                    UserRole.LANDLORD,
                    "Current user must have LANDLORD role");
            User tenant = authUserService.requireUserWithRole(
                    request.getTenantId(),
                    UserRole.TENANT,
                    "Target user must have TENANT role");

            if (landlord.getId().equals(tenant.getId())) {
                throw new BadRequestException("Landlord and tenant must be different users");
            }

            OffsetDateTime now = OffsetDateTime.now();

            Claim claim = Claim.builder()
                    .landlordId(landlord.getId())
                    .tenantId(tenant.getId())
                    .status(ClaimStatus.SUBMITTED)
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .claimedAmount(request.getClaimedAmount())
                    .currency(request.getCurrency())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            Claim saved = claimRepository.save(claim);

            List<String> objectKeys = request.getAttachmentKeys();
            if (objectKeys != null && !objectKeys.isEmpty()) {
                minioService.attachExistingObjectsToClaim(saved, landlord.getId(), objectKeys);
            }

            statusHistoryRepository.save(ClaimStatusHistory.builder()
                    .claim(saved)
                    .fromStatus(null)
                    .toStatus(ClaimStatus.SUBMITTED)
                    .actorId(landlord.getId())
                    .createdAt(now)
                    .build());

            return buildResponse(saved, toDownloadUrls(objectKeys));
        });
    }

    public ClaimResponse intakeDecision(Long claimId, Long adminUserId, IntakeDecisionRequest request) {
        return txTemplate.execute(status -> {
            Claim claim = requireClaim(claimId);
            User admin = authUserService.requireUserWithRole(
                    adminUserId,
                    UserRole.ADMIN,
                    "Current user must have ADMIN role");

            if (claim.getStatus() != ClaimStatus.SUBMITTED && claim.getStatus() != ClaimStatus.INTAKE_REVIEW) {
                throw new ConflictException("Claim is not in intake review stage");
            }

            ClaimStatus from = claim.getStatus();
            ClaimStatus to = request.isNeedMoreInfo()
                    ? ClaimStatus.NEED_ADDITIONAL_INFO
                    : ClaimStatus.UNDER_ASSESSMENT;

            if (request.isNeedMoreInfo() && (request.getComment() == null || request.getComment().isBlank())) {
                throw new BadRequestException("Comment is required when requesting additional info");
            }

            claim.setAdminReviewerId(admin.getId());
            claim.setStatus(to);
            claim.setUpdatedAt(OffsetDateTime.now());
            claimRepository.save(claim);

            if (request.getComment() != null && !request.getComment().isBlank()) {
                claimMessageRepository.save(ClaimMessage.builder()
                        .claim(claim)
                        .userId(admin.getId())
                        .messageType(
                                request.isNeedMoreInfo() ? CommentType.ADDITIONAL_INFO_REQUEST : CommentType.ADMIN_NOTE)
                        .body(request.getComment())
                        .createdAt(OffsetDateTime.now())
                        .build());
            }

            statusHistoryRepository.save(ClaimStatusHistory.builder()
                    .claim(claim)
                    .fromStatus(from)
                    .toStatus(to)
                    .actorId(admin.getId())
                    .createdAt(OffsetDateTime.now())
                    .build());

            return buildResponse(claim, loadAttachmentUrls(claim.getId()));
        });
    }

    public ClaimResponse assessClaim(Long claimId, Long adminUserId, AssessmentRequest request) {
        return txTemplate.execute(status -> {
            Claim claim = requireClaim(claimId);
            if (claim.getStatus() != ClaimStatus.UNDER_ASSESSMENT
                    && claim.getStatus() != ClaimStatus.AWAITING_TENANT_RESPONSE) {
                throw new ConflictException("Claim not in assessment stage");
            }

            User admin = authUserService.requireUserWithRole(
                    adminUserId,
                    UserRole.ADMIN,
                    "Current user must have ADMIN role");
            if (claim.getAdminReviewerId() == null) {
                claim.setAdminReviewerId(admin.getId());
            }

            OffsetDateTime now = OffsetDateTime.now();
            claim.setAssessmentAmount(request.getAssessmentAmount());
            claim.setAssessmentNotes(request.getAssessmentNotes());
            claim.setUpdatedAt(now);

            ClaimStatus from = claim.getStatus();
            ClaimStatus to;
            if (request.isPenaltyGrounds()) {
                to = ClaimStatus.AWAITING_TENANT_RESPONSE;
            } else {
                to = ClaimStatus.CLOSED_NO_PENALTY;
                claim.setClosedAt(now);
                claim.setDecidedAt(now);
                claim.setPenaltyAmount(null);
            }
            claim.setStatus(to);
            claimRepository.save(claim);

            statusHistoryRepository.save(ClaimStatusHistory.builder()
                    .claim(claim)
                    .fromStatus(from)
                    .toStatus(to)
                    .actorId(admin.getId())
                    .createdAt(now)
                    .build());

            claimMessageRepository.save(ClaimMessage.builder()
                    .claim(claim)
                    .userId(admin.getId())
                    .messageType(CommentType.ADMIN_NOTE)
                    .body(buildAssessmentNote(request))
                    .createdAt(now)
                    .build());

            return buildResponse(claim, loadAttachmentUrls(claim.getId()));
        });
    }

    public ClaimResponse tenantResponse(Long claimId, Long tenantUserId, TenantResponseRequest request) {
        return txTemplate.execute(status -> {
            Claim claim = requireClaim(claimId);
            if (claim.getStatus() != ClaimStatus.AWAITING_TENANT_RESPONSE) {
                throw new ConflictException("Claim is not waiting for tenant response");
            }

            User tenant = authUserService.requireUserWithRole(
                    tenantUserId,
                    UserRole.TENANT,
                    "Current user must have TENANT role");
            if (!claim.getTenantId().equals(tenant.getId())) {
                throw new AccessDeniedException("Current tenant is not the respondent of this claim");
            }

            OffsetDateTime now = OffsetDateTime.now();

            ClaimMessage msg = ClaimMessage.builder()
                    .claim(claim)
                    .userId(tenant.getId())
                    .messageType(CommentType.TENANT_RESPONSE)
                    .body(request.getComment())
                    .createdAt(now)
                    .build();
            claimMessageRepository.save(msg);

            List<String> keys = mergeAttachmentKeys(request.getAttachmentKeys(), request.getAttachmentIds());
            if (!keys.isEmpty()) {
                minioService.attachExistingObjectsToClaim(claim, tenant.getId(), keys, msg);
            }

            ClaimStatus from = claim.getStatus();
            claim.setStatus(ClaimStatus.SUPPORT_REVIEW);
            claim.setUpdatedAt(now);
            claimRepository.save(claim);

            statusHistoryRepository.save(ClaimStatusHistory.builder()
                    .claim(claim)
                    .fromStatus(from)
                    .toStatus(ClaimStatus.SUPPORT_REVIEW)
                    .actorId(tenant.getId())
                    .createdAt(now)
                    .build());

            return buildResponse(claim, loadAttachmentUrls(claim.getId()));
        });
    }

    public ClaimResponse additionalInfoReply(Long claimId, Long landlordUserId, AdditionalInfoReplyRequest request) {
        return txTemplate.execute(status -> {
            Claim claim = requireClaim(claimId);
            if (claim.getStatus() != ClaimStatus.NEED_ADDITIONAL_INFO) {
                throw new ConflictException("Claim is not waiting for additional info");
            }

            User landlord = authUserService.requireUserWithRole(
                    landlordUserId,
                    UserRole.LANDLORD,
                    "Current user must have LANDLORD role");
            if (!claim.getLandlordId().equals(landlord.getId())) {
                throw new AccessDeniedException("Current landlord is not the owner of this claim");
            }

            OffsetDateTime now = OffsetDateTime.now();

            ClaimMessage msg = ClaimMessage.builder()
                    .claim(claim)
                    .userId(landlord.getId())
                    .messageType(CommentType.ADDITIONAL_INFO_REPLY)
                    .body(request.getComment())
                    .createdAt(now)
                    .build();
            claimMessageRepository.save(msg);

            List<String> keys = mergeAttachmentKeys(request.getAttachmentKeys(), request.getAttachmentIds());
            if (!keys.isEmpty()) {
                minioService.attachExistingObjectsToClaim(claim, landlord.getId(), keys, msg);
            }

            ClaimStatus from = claim.getStatus();
            claim.setStatus(ClaimStatus.UNDER_ASSESSMENT);
            claim.setUpdatedAt(now);
            claimRepository.save(claim);

            statusHistoryRepository.save(ClaimStatusHistory.builder()
                    .claim(claim)
                    .fromStatus(from)
                    .toStatus(ClaimStatus.UNDER_ASSESSMENT)
                    .actorId(landlord.getId())
                    .createdAt(now)
                    .build());

            return buildResponse(claim, loadAttachmentUrls(claim.getId()));
        });
    }

    public ClaimResponse supportDecision(Long claimId, Long adminUserId, SupportDecisionRequest request) {
        return txTemplate.execute(status -> {
            Claim claim = requireClaim(claimId);
            if (claim.getStatus() != ClaimStatus.SUPPORT_REVIEW) {
                throw new ConflictException("Claim not in support review stage");
            }

            User admin = authUserService.requireUserWithRole(
                    adminUserId,
                    UserRole.ADMIN,
                    "Current user must have ADMIN role");
            if (claim.getAdminReviewerId() == null) {
                claim.setAdminReviewerId(admin.getId());
            }

            OffsetDateTime now = OffsetDateTime.now();
            ClaimStatus from = claim.getStatus();
            ClaimStatus to;

            if (request.isApplyPenalty()) {
                if (request.getPenaltyAmount() == null) {
                    throw new BadRequestException("penaltyAmount required when applyPenalty=true");
                }
                to = ClaimStatus.PENALTY_APPLIED;
                claim.setPenaltyAmount(request.getPenaltyAmount());
                claim.setPenaltyCurrency(request.getPenaltyCurrency());
                authUserService.incrementPenaltyCount(claim.getTenantId());
            } else {
                to = ClaimStatus.CLOSED_NO_PENALTY;
                claim.setPenaltyAmount(null);
            }

            claim.setStatus(to);
            claim.setResolutionNote(request.getNote());
            claim.setDecidedAt(now);
            claim.setClosedAt(now);
            claim.setUpdatedAt(now);
            claimRepository.save(claim);

            statusHistoryRepository.save(ClaimStatusHistory.builder()
                    .claim(claim)
                    .fromStatus(from)
                    .toStatus(to)
                    .actorId(admin.getId())
                    .createdAt(now)
                    .build());

            if (request.getNote() != null && !request.getNote().isBlank()) {
                claimMessageRepository.save(ClaimMessage.builder()
                        .claim(claim)
                        .userId(admin.getId())
                        .messageType(CommentType.ADMIN_NOTE)
                        .body(request.getNote())
                        .createdAt(now)
                        .build());
            }

            return buildResponse(claim, loadAttachmentUrls(claim.getId()));
        });
    }

    public ClaimResponse getClaim(Long id, Long actorUserId, UserRole actorRole) {
        return readOnlyTxTemplate.execute(status -> {
            Claim claim = claimRepository.findWithAllById(id)
                    .orElseThrow(() -> ResourceNotFoundException.of(Claim.class, "id", id));

            ensureCanView(claim, actorUserId, actorRole);

            return buildResponse(claim, loadAttachmentUrls(claim.getId()));
        });
    }

    public List<ClaimResponse> getClaimsForLandlord(Long landlordId, boolean openOnly,
            Long actorUserId, UserRole actorRole) {
        return readOnlyTxTemplate.execute(status -> {
            if (actorRole != UserRole.ADMIN && !landlordId.equals(actorUserId)) {
                throw new AccessDeniedException("Cannot list claims of another landlord");
            }

            List<Claim> claims = claimRepository.findWithAttachmentsByLandlordId(landlordId);
            java.util.Map<Long, List<String>> attachmentsMap = preloadAttachmentUrls(claims);
            return claims.stream()
                    .filter(c -> !openOnly || isOpenStatus(c.getStatus()))
                    .map(c -> buildResponse(c, attachmentsMap.getOrDefault(c.getId(), java.util.Collections.emptyList())))
                    .toList();
        });
    }

    public List<String> getAdditionalInfoAttachmentKeys(Long claimId, Long actorUserId, UserRole actorRole) {
        return readOnlyTxTemplate.execute(status -> {
            Claim claim = claimRepository.findById(claimId)
                    .orElseThrow(() -> ResourceNotFoundException.of(Claim.class, "id", claimId));

            ensureCanView(claim, actorUserId, actorRole);

            List<Long> messageIds = claimMessageRepository
                    .findByClaimIdAndMessageTypeOrderByCreatedAtAsc(claim.getId(), CommentType.ADDITIONAL_INFO_REPLY)
                    .stream()
                    .map(ClaimMessage::getId)
                    .toList();
            if (messageIds.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            return claimAttachmentRepository.findByMessageIdIn(messageIds)
                    .stream()
                    .map(att -> minioService.presignGetUrl(att.getObjectKey()))
                    .toList();
        });
    }

    private String buildAssessmentNote(AssessmentRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Оценка: ").append(request.getAssessmentAmount());
        if (request.getAssessmentNotes() != null && !request.getAssessmentNotes().isBlank()) {
            sb.append(". ").append(request.getAssessmentNotes());
        }
        sb.append(". Основания для штрафа: ").append(request.isPenaltyGrounds() ? "да" : "нет");
        return sb.toString();
    }

    private Claim requireClaim(Long id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(Claim.class, "id", id));
    }

    private void ensureCanView(Claim claim, Long actorUserId, UserRole actorRole) {
        if (actorRole == UserRole.ADMIN) {
            return;
        }
        Long landlordId = claim.getLandlordId();
        Long tenantId = claim.getTenantId();
        if (!landlordId.equals(actorUserId) && !tenantId.equals(actorUserId)) {
            throw new AccessDeniedException("Access to this claim is denied");
        }
    }

    private boolean isOpenStatus(ClaimStatus status) {
        return status != ClaimStatus.CLOSED_NO_PENALTY && status != ClaimStatus.PENALTY_APPLIED;
    }

    private List<String> loadAttachmentUrls(Long claimId) {
        return claimAttachmentRepository.findByClaimId(claimId)
                .stream()
                .map(att -> minioService.presignGetUrl(att.getObjectKey()))
                .toList();
    }

    private List<String> toDownloadUrls(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return minioService.normalizeObjectKeys(keys).stream()
                .map(minioService::presignGetUrl)
                .toList();
    }

    private List<String> mergeAttachmentKeys(List<String> keys, List<Long> ids) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        if (keys != null && !keys.isEmpty()) {
            result.addAll(minioService.normalizeObjectKeys(keys));
        }
        if (ids != null && !ids.isEmpty()) {
            claimAttachmentRepository.findAllById(ids)
                    .forEach(att -> result.add(att.getObjectKey()));
        }
        return new java.util.ArrayList<>(result);
    }

    private java.util.Map<Long, List<String>> preloadAttachmentUrls(List<Claim> claims) {
        if (claims.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        List<Long> ids = claims.stream().map(Claim::getId).toList();
        return claimAttachmentRepository.findByClaimIdIn(ids).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        att -> att.getClaim().getId(),
                        java.util.stream.Collectors.mapping(
                                att -> minioService.presignGetUrl(att.getObjectKey()),
                                java.util.stream.Collectors.toList())));
    }

    private ClaimResponse buildResponse(Claim claim, List<String> attachments) {
        return ClaimResponse.builder()
                .id(claim.getId())
                .landlordId(claim.getLandlordId())
                .tenantId(claim.getTenantId())
                .status(claim.getStatus())
                .title(claim.getTitle())
                .description(claim.getDescription())
                .claimedAmount(claim.getClaimedAmount())
                .currency(claim.getCurrency())
                .createdAt(claim.getCreatedAt())
                .attachments(attachments)
                .build();
    }
}
