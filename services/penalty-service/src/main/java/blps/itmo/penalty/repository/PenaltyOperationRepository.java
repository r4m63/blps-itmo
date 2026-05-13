package blps.itmo.penalty.repository;

import blps.itmo.penalty.domain.PenaltyOperation;
import blps.itmo.penalty.domain.PenaltyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PenaltyOperationRepository extends JpaRepository<PenaltyOperation, Integer> {

    Optional<PenaltyOperation> findByClaimId(Integer claimId);

    List<PenaltyOperation> findByTenantId(Integer tenantId);

    List<PenaltyOperation> findByStatus(PenaltyStatus status);
}
