package blps.itmo.repository.business;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import blps.itmo.entity.business.Claim;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    @EntityGraph("Claim.withAll")
    Optional<Claim> findWithAllById(Long id);

    @EntityGraph("Claim.withAttachments")
    List<Claim> findWithAttachmentsByLandlordId(Long landlordId);

    List<Claim> findByTenantId(Long tenantId);
}
