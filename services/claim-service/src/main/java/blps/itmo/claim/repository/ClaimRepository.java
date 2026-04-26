package blps.itmo.claim.repository;

import java.util.List;
import java.time.OffsetDateTime;

import org.springframework.data.jpa.repository.JpaRepository;

import blps.itmo.claim.domain.Claim;
import blps.itmo.claim.domain.ClaimStatus;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
    List<Claim> findByLandlordIdOrTenantId(Long landlordId, Long tenantId);

    List<Claim> findByStatusNotIn(List<ClaimStatus> statuses);

    List<Claim> findByStatusAndUpdatedAtBefore(ClaimStatus status, OffsetDateTime updatedAt);
}
