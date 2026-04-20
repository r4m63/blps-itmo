package blps.itmo.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import blps.itmo.entity.business.Claim;
import blps.itmo.entity.business.ClaimStatus;
import blps.itmo.entity.business.ClaimStatusHistory;
import blps.itmo.repository.business.ClaimRepository;
import blps.itmo.repository.business.ClaimStatusHistoryRepository;

@Service
public class UserService {

    private final AuthUserService authUserService;
    private final ClaimRepository claimRepository;
    private final ClaimStatusHistoryRepository statusHistoryRepository;
    private final TransactionTemplate txTemplate;

    public UserService(AuthUserService authUserService,
            ClaimRepository claimRepository,
            ClaimStatusHistoryRepository statusHistoryRepository,
            @Qualifier("jtaTransactionTemplate") TransactionTemplate txTemplate) {
        this.authUserService = authUserService;
        this.claimRepository = claimRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.txTemplate = txTemplate;
    }

    public int deactivateUser(Long userId) {
        return txTemplate.execute(status -> {
            authUserService.deactivateUser(userId);

            List<Claim> asLandlord = claimRepository.findWithAttachmentsByLandlordId(userId);
            List<Claim> asTenant = claimRepository.findByTenantId(userId);

            OffsetDateTime now = OffsetDateTime.now();
            int closedCount = 0;

            for (Claim claim : asLandlord) {
                if (isOpenStatus(claim.getStatus())) {
                    closedCount += closeClaim(claim, userId, now);
                }
            }
            for (Claim claim : asTenant) {
                if (isOpenStatus(claim.getStatus())) {
                    closedCount += closeClaim(claim, userId, now);
                }
            }

            return closedCount;
        });
    }

    private int closeClaim(Claim claim, Long actorId, OffsetDateTime now) {
        ClaimStatus from = claim.getStatus();
        claim.setStatus(ClaimStatus.CLOSED_NO_PENALTY);
        claim.setPenaltyAmount(null);
        claim.setResolutionNote("Closed due to user deactivation");
        claim.setDecidedAt(now);
        claim.setClosedAt(now);
        claim.setUpdatedAt(now);
        claimRepository.save(claim);

        statusHistoryRepository.save(ClaimStatusHistory.builder()
                .claim(claim)
                .fromStatus(from)
                .toStatus(ClaimStatus.CLOSED_NO_PENALTY)
                .actorId(actorId)
                .note("Closed due to user deactivation")
                .createdAt(now)
                .build());

        return 1;
    }

    private boolean isOpenStatus(ClaimStatus status) {
        return status != ClaimStatus.CLOSED_NO_PENALTY && status != ClaimStatus.PENALTY_APPLIED;
    }
}
