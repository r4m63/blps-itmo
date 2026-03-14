package blps.itmo.repository;

import blps.itmo.entity.Claim;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    @EntityGraph(value = "Claim.withAll")
    Optional<Claim> findWithAllById(Long id);

    @EntityGraph(value = "Claim.withAttachments")
    List<Claim> findWithAttachmentsByLandlordId(Long landlordId);

    List<Claim> findByTenantId(Long tenantId);
}
