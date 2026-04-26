package blps.itmo.penalty.repository;

import java.util.List;
import java.time.OffsetDateTime;

import org.springframework.data.jpa.repository.JpaRepository;

import blps.itmo.penalty.domain.PenaltyOperation;
import blps.itmo.penalty.domain.PenaltyOperationStatus;

public interface PenaltyOperationRepository extends JpaRepository<PenaltyOperation, Long> {
    List<PenaltyOperation> findTop20ByStatusOrderByCreatedAtAsc(PenaltyOperationStatus status);

    List<PenaltyOperation> findByClaimIdOrderByCreatedAtDesc(Long claimId);

    List<PenaltyOperation> findByStatusAndCreatedAtBefore(PenaltyOperationStatus status, OffsetDateTime createdAt);
}
