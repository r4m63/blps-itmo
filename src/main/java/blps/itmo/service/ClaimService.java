package blps.itmo.service;

import blps.itmo.dto.ClaimResponse;
import blps.itmo.dto.CreateClaimRequest;
import blps.itmo.dto.IntakeDecisionRequest;
import blps.itmo.entity.Claim;
import blps.itmo.entity.ClaimMessage;
import blps.itmo.entity.ClaimStatus;
import blps.itmo.entity.ClaimStatusHistory;
import blps.itmo.entity.CommentType;
import blps.itmo.entity.User;
import blps.itmo.repository.ClaimAttachmentRepository;
import blps.itmo.repository.ClaimMessageRepository;
import blps.itmo.repository.ClaimRepository;
import blps.itmo.repository.ClaimStatusHistoryRepository;
import blps.itmo.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public ClaimResponse createClaim(CreateClaimRequest request) {
        User landlord = userRepository.findById(request.getLandlordId())
                .orElseThrow(() -> new IllegalArgumentException("Landlord not found"));
        User tenant = userRepository.findById(request.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

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

        return ClaimResponse.builder()
                .id(saved.getId())
                .landlordId(saved.getLandlord().getId())
                .tenantId(saved.getTenant().getId())
                .status(saved.getStatus())
                .title(saved.getTitle())
                .description(saved.getDescription())
                .claimedAmount(saved.getClaimedAmount())
                .currency(saved.getCurrency())
                .createdAt(saved.getCreatedAt())
                .attachmentKeys(objectKeys)
                .build();
    }

    @Transactional
    public ClaimResponse intakeDecision(Long claimId, IntakeDecisionRequest request) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found"));
        User admin = userRepository.findById(request.getAdminId())
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        ClaimStatus from = claim.getStatus();
        ClaimStatus to = request.isNeedMoreInfo() ? ClaimStatus.NEED_ADDITIONAL_INFO : ClaimStatus.UNDER_ASSESSMENT;

        if (request.isNeedMoreInfo() && (request.getComment() == null || request.getComment().isBlank())) {
            throw new IllegalArgumentException("Comment is required when requesting additional info");
        }

        claim.setAdminReviewer(admin);
        claim.setStatus(to);
        claim.setUpdatedAt(OffsetDateTime.now());
        claimRepository.save(claim);

        if (request.getComment() != null && !request.getComment().isBlank()) {
            claimMessageRepository.save(ClaimMessage.builder()
                    .claim(claim)
                    .user(admin)
                    .messageType(request.isNeedMoreInfo() ? CommentType.ADDITIONAL_INFO_REQUEST : CommentType.ADMIN_NOTE)
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
                .attachmentKeys(loadAttachmentKeys(claim.getId()))
                .build();
    }

    private List<String> loadAttachmentKeys(Long claimId) {
        return claimAttachmentRepository.findByClaimId(claimId)
                .stream()
                .map(att -> att.getObjectKey())
                .toList();
    }

    @Transactional(readOnly = true)
    public ClaimResponse getClaim(Long id) {
        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found"));
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
                .attachmentKeys(loadAttachmentKeys(claim.getId()))
                .build();
    }
}
