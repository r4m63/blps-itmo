package blps.itmo.repository;

import blps.itmo.entity.ClaimStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimStatusHistoryRepository extends JpaRepository<ClaimStatusHistory, Long> {
    List<ClaimStatusHistory> findByClaimIdOrderByCreatedAtAsc(Long claimId);
}
