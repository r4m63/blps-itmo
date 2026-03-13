package blps.itmo.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import blps.itmo.dto.ClaimResponse;
import blps.itmo.dto.CreateClaimRequest;
import blps.itmo.entity.Claim;
import blps.itmo.entity.ClaimStatus;
import blps.itmo.entity.ClaimStatusHistory;
import blps.itmo.entity.User;
import blps.itmo.repository.ClaimRepository;
import blps.itmo.repository.ClaimStatusHistoryRepository;
import blps.itmo.repository.UserRepository;

@Service
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final ClaimStatusHistoryRepository statusHistoryRepository;
    private final MinioService minioService;

    public ClaimService(ClaimRepository claimRepository,
            UserRepository userRepository,
            ClaimStatusHistoryRepository statusHistoryRepository,
            MinioService minioService) {
        this.claimRepository = claimRepository;
        this.userRepository = userRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.minioService = minioService;
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
}
