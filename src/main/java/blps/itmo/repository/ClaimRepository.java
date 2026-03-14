package blps.itmo.repository;

import blps.itmo.entity.Claim;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    @EntityGraph(attributePaths = {
            "landlord",
            "tenant",
            "adminReviewer",
            "attachments",
            "messages",
            "statusHistory"
    })
    Optional<Claim> findWithAllById(Long id);

    @EntityGraph(attributePaths = {
            "landlord",
            "tenant",
            "adminReviewer",
            "attachments"
    })
    List<Claim> findWithAttachmentsByLandlordId(Long landlordId);

    List<Claim> findByTenantId(Long tenantId);
}
