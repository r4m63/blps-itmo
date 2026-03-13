package blps.itmo.repository;

import blps.itmo.entity.Claim;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
    List<Claim> findByLandlordId(Long landlordId);
    List<Claim> findByTenantId(Long tenantId);
}
