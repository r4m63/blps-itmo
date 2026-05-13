package blps.itmo.claim.repository;

import blps.itmo.claim.domain.Claim;
import blps.itmo.claim.domain.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, Integer> {

    List<Claim> findByLandlordId(Integer landlordId);

    List<Claim> findByTenantId(Integer tenantId);

    List<Claim> findByStatus(ClaimStatus status);

    List<Claim> findByLandlordIdOrTenantId(Integer landlordId, Integer tenantId);
}
