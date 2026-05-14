package blps.itmo.penalty.repository;

import blps.itmo.penalty.domain.PenaltyOperation;
import blps.itmo.penalty.domain.PenaltyStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PenaltyOperationRepository extends JpaRepository<PenaltyOperation, Integer> {

    Optional<PenaltyOperation> findByClaimId(Integer claimId);

    List<PenaltyOperation> findByTenantId(Integer tenantId);

    List<PenaltyOperation> findByStatus(PenaltyStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PenaltyOperation> findTop50ByStatus(PenaltyStatus status);
}
