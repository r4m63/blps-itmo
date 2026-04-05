package blps.itmo.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import blps.itmo.dto.AdditionalInfoReplyRequest;
import blps.itmo.dto.AssessmentRequest;
import blps.itmo.dto.ClaimResponse;
import blps.itmo.dto.CreateClaimRequest;
import blps.itmo.dto.IntakeDecisionRequest;
import blps.itmo.dto.SupportDecisionRequest;
import blps.itmo.dto.TenantResponseRequest;
import blps.itmo.entity.Claim;
import blps.itmo.entity.ClaimMessage;
import blps.itmo.entity.ClaimStatus;
import blps.itmo.entity.ClaimStatusHistory;
import blps.itmo.entity.CommentType;
import blps.itmo.entity.User;
import blps.itmo.entity.UserRole;
import blps.itmo.exception.BadRequestException;
import blps.itmo.exception.ConflictException;
import blps.itmo.exception.ResourceNotFoundException;
import blps.itmo.repository.ClaimAttachmentRepository;
import blps.itmo.repository.ClaimMessageRepository;
import blps.itmo.repository.ClaimRepository;
import blps.itmo.repository.ClaimStatusHistoryRepository;
import blps.itmo.repository.UserRepository;

/**
 * Бизнес-логика обработки заявок на штрафные санкции.
 * <p>
 * Все методы, модифицирующие состояние, принимают {@code actorUserId} —
 * идентификатор аутентифицированного пользователя, извлечённый контроллером
 * из {@code SecurityContext}. Клиент не может подделать актора.
 * <p>
 * Для read-операций используется дополнительная проверка владения:
 * арендодатель/арендатор видит только свои заявки, администратор — любые.
 */
@Service
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final ClaimStatusHistoryRepository statusHistoryRepository;
    private final MinioService minioService;
    private final ClaimMessageRepository claimMessageRepository;
    private final ClaimAttachmentRepository claimAttachmentRepository;

    public ClaimService(ClaimRepository claimRepository,
            UserRepository userRepository,
            ClaimStatusHistoryRepository statusHistoryRepository,
            MinioService minioService,
            ClaimMessageRepository claimMessageRepository,
            ClaimAttachmentRepository claimAttachmentRepository) {
        this.claimRepository = claimRepository;
        this.userRepository = userRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.minioService = minioService;
        this.claimMessageRepository = claimMessageRepository;
        this.claimAttachmentRepository = claimAttachmentRepository;
    }

    // =================================================================
    // Commands (write operations)
    // =================================================================

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ClaimResponse createClaim(Long landlordUserId, CreateClaimRequest request) {
        User landlord = requireUser(landlordUserId);
        User tenant = requireUser(request.getTenantId());

        if (landlord.getId().equals(tenant.getId())) {
            throw new BadRequestException("Landlord and tenant must be different users");
        }
        if (tenant.getRole() != UserRole.TENANT) {
            throw new BadRequestException("Target user must have TENANT role");
        }

        OffsetDateTime now = OffsetDateTime.now();

        Claim claim = Claim.builder()
                .landlord(landlord)
                .tenant(tenant)
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
            minioService.attachExistingObjectsToClaim(saved, landlord, objectKeys);
        }

        statusHistoryRepository.save(ClaimStatusHistory.builder()
                .claim(saved)
                .fromStatus(null)
                .toStatus(ClaimStatus.SUBMITTED)
                .actor(landlord)
                .createdAt(now)
                .build());

        return buildResponse(saved, toDownloadUrls(objectKeys));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ClaimResponse intakeDecision(Long claimId, Long adminUserId, IntakeDecisionRequest request) {
        Claim claim = requireClaim(claimId);
        User admin = requireUser(adminUserId);

        if (claim.getStatus() != ClaimStatus.SUBMITTED && claim.getStatus() != ClaimStatus.INTAKE_REVIEW) {
            throw new ConflictException("Claim is not in intake review stage");
        }

        ClaimStatus from = claim.getStatus();
        ClaimStatus to = request.isNeedMoreInfo() ? ClaimStatus.NEED_ADDITIONAL_INFO : ClaimStatus.UNDER_ASSESSMENT;

        if (request.isNeedMoreInfo() && (request.getComment() == null || request.getComment().isBlank())) {
            throw new BadRequestException("Comment is required when requesting additional info");
        }

        claim.setAdminReviewer(admin);
        claim.setStatus(to);
        claim.setUpdatedAt(OffsetDateTime.now());
        claimRepository.save(claim);

        if (request.getComment() != null && !request.getComment().isBlank()) {
            claimMessageRepository.save(ClaimMessage.builder()
                    .claim(claim)
                    .user(admin)
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
                .actor(admin)
                .createdAt(OffsetDateTime.now())
                .build());

        return buildResponse(claim, loadAttachmentUrls(claim.getId()));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ClaimResponse assessClaim(Long claimId, Long adminUserId, AssessmentRequest request) {
        Claim claim = requireClaim(claimId);
        if (claim.getStatus() != ClaimStatus.UNDER_ASSESSMENT
                && claim.getStatus() != ClaimStatus.AWAITING_TENANT_RESPONSE) {
            throw new ConflictException("Claim not in assessment stage");
        }
        User admin = requireUser(adminUserId);
        if (claim.getAdminReviewer() == null) {
            claim.setAdminReviewer(admin);
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
                .actor(admin)
                .createdAt(now)
                .build());

        claimMessageRepository.save(ClaimMessage.builder()
                .claim(claim)
                .user(admin)
                .messageType(CommentType.ADMIN_NOTE)
                .body(buildAssessmentNote(request))
                .createdAt(now)
                .build());

        return buildResponse(claim, loadAttachmentUrls(claim.getId()));
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

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ClaimResponse tenantResponse(Long claimId, Long tenantUserId, TenantResponseRequest request) {
        Claim claim = requireClaim(claimId);
        if (claim.getStatus() != ClaimStatus.AWAITING_TENANT_RESPONSE) {
            throw new ConflictException("Claim is not waiting for tenant response");
        }
        User tenant = requireUser(tenantUserId);
        if (!claim.getTenant().getId().equals(tenant.getId())) {
            throw new AccessDeniedException("Current tenant is not the respondent of this claim");
        }
        OffsetDateTime now = OffsetDateTime.now();

        ClaimMessage msg = ClaimMessage.builder()
                .claim(claim)
                .user(tenant)
                .messageType(CommentType.TENANT_RESPONSE)
                .body(request.getComment())
                .createdAt(now)
                .build();
        claimMessageRepository.save(msg);

        List<String> keys = mergeAttachmentKeys(request.getAttachmentKeys(), request.getAttachmentIds());
        if (!keys.isEmpty()) {
            minioService.attachExistingObjectsToClaim(claim, tenant, keys, msg);
        }

        ClaimStatus from = claim.getStatus();
        claim.setStatus(ClaimStatus.SUPPORT_REVIEW);
        claim.setUpdatedAt(now);
        claimRepository.save(claim);

        statusHistoryRepository.save(ClaimStatusHistory.builder()
                .claim(claim)
                .fromStatus(from)
                .toStatus(ClaimStatus.SUPPORT_REVIEW)
                .actor(tenant)
                .createdAt(now)
                .build());

        return buildResponse(claim, loadAttachmentUrls(claim.getId()));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ClaimResponse supportDecision(Long claimId, Long adminUserId, SupportDecisionRequest request) {
        Claim claim = requireClaim(claimId);
        if (claim.getStatus() != ClaimStatus.SUPPORT_REVIEW) {
            throw new ConflictException("Claim not in support review stage");
        }
        User admin = requireUser(adminUserId);
        if (claim.getAdminReviewer() == null) {
            claim.setAdminReviewer(admin);
        }
        OffsetDateTime now = OffsetDateTime.now();
        ClaimStatus from = claim.getStatus();
        ClaimStatus to;
        if (request.isApplyPenalty()) {
            to = ClaimStatus.PENALTY_APPLIED;
            if (request.getPenaltyAmount() == null) {
                throw new BadRequestException("penaltyAmount required when applyPenalty=true");
            }
            claim.setPenaltyAmount(request.getPenaltyAmount());
            claim.setPenaltyCurrency(request.getPenaltyCurrency());
        } else {
            to = ClaimStatus.CLOSED_NO_PENALTY;
            claim.setPenaltyAmount(null);
        }
        claim.setStatus(to);
        claim.setDecidedAt(now);
        claim.setClosedAt(now);
        claim.setUpdatedAt(now);
        claimRepository.save(claim);

        statusHistoryRepository.save(ClaimStatusHistory.builder()
                .claim(claim)
                .fromStatus(from)
                .toStatus(to)
                .actor(admin)
                .createdAt(now)
                .build());

        if (request.getNote() != null && !request.getNote().isBlank()) {
            claimMessageRepository.save(ClaimMessage.builder()
                    .claim(claim)
                    .user(admin)
                    .messageType(CommentType.ADMIN_NOTE)
                    .body(request.getNote())
                    .createdAt(now)
                    .build());
        }

        return buildResponse(claim, loadAttachmentUrls(claim.getId()));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ClaimResponse additionalInfoReply(Long claimId, Long landlordUserId, AdditionalInfoReplyRequest request) {
        Claim claim = requireClaim(claimId);
        if (claim.getStatus() != ClaimStatus.NEED_ADDITIONAL_INFO) {
            throw new ConflictException("Claim is not waiting for additional info");
        }
        User landlord = requireUser(landlordUserId);
        if (!claim.getLandlord().getId().equals(landlord.getId())) {
            throw new AccessDeniedException("Current landlord is not the owner of this claim");
        }

        OffsetDateTime now = OffsetDateTime.now();

        ClaimMessage msg = ClaimMessage.builder()
                .claim(claim)
                .user(landlord)
                .messageType(CommentType.ADDITIONAL_INFO_REPLY)
                .body(request.getComment())
                .createdAt(now)
                .build();
        claimMessageRepository.save(msg);

        List<String> keys = mergeAttachmentKeys(request.getAttachmentKeys(), request.getAttachmentIds());
        if (!keys.isEmpty()) {
            minioService.attachExistingObjectsToClaim(claim, landlord, keys, msg);
        }

        ClaimStatus from = claim.getStatus();
        claim.setStatus(ClaimStatus.UNDER_ASSESSMENT);
        claim.setUpdatedAt(now);
        claimRepository.save(claim);

        statusHistoryRepository.save(ClaimStatusHistory.builder()
                .claim(claim)
                .fromStatus(from)
                .toStatus(ClaimStatus.UNDER_ASSESSMENT)
                .actor(landlord)
                .createdAt(now)
                .build());

        return buildResponse(claim, loadAttachmentUrls(claim.getId()));
    }

    // =================================================================
    // Queries (read operations)
    // =================================================================

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ClaimResponse getClaim(Long id, Long actorUserId, UserRole actorRole) {
        Claim claim = claimRepository.findWithAllById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(Claim.class, "id", id));

        ensureCanView(claim, actorUserId, actorRole);

        return buildResponse(claim, loadAttachmentUrls(claim.getId()));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ClaimResponse> getClaimsForLandlord(Long landlordId, boolean openOnly,
            Long actorUserId, UserRole actorRole) {
        // LANDLORD может запрашивать только свои заявки. ADMIN — любые.
        if (actorRole != UserRole.ADMIN && !landlordId.equals(actorUserId)) {
            throw new AccessDeniedException("Cannot list claims of another landlord");
        }

        List<Claim> claims = claimRepository.findWithAttachmentsByLandlordId(landlordId);
        java.util.Map<Long, List<String>> attachmentsMap = preloadAttachmentUrls(claims);
        return claims.stream()
                .filter(c -> !openOnly || isOpenStatus(c.getStatus()))
                .map(c -> buildResponse(c, attachmentsMap.getOrDefault(c.getId(), java.util.Collections.emptyList())))
                .toList();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<String> getAdditionalInfoAttachmentKeys(Long claimId, Long actorUserId, UserRole actorRole) {
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
    }

    // =================================================================
    // Helpers
    // =================================================================

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(User.class, "id", id));
    }

    private Claim requireClaim(Long id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(Claim.class, "id", id));
    }

    /**
     * Ownership-проверка: ADMIN видит всё, LANDLORD/TENANT — только заявки,
     * где он является стороной.
     */
    private void ensureCanView(Claim claim, Long actorUserId, UserRole actorRole) {
        if (actorRole == UserRole.ADMIN) {
            return;
        }
        Long landlordId = claim.getLandlord().getId();
        Long tenantId = claim.getTenant().getId();
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
        if (claims.isEmpty())
            return java.util.Collections.emptyMap();
        List<Long> ids = claims.stream().map(Claim::getId).toList();
        return claimAttachmentRepository.findByClaimIdIn(ids).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        att -> att.getClaim().getId(),
                        java.util.stream.Collectors.mapping(att -> minioService.presignGetUrl(att.getObjectKey()),
                                java.util.stream.Collectors.toList())));
    }

    private ClaimResponse buildResponse(Claim claim, List<String> attachments) {
        return ClaimResponse.builder()
                .id(claim.getId())
                .landlordId(claim.getLandlord().getId())
                .tenantId(claim.getTenant().getId())
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
