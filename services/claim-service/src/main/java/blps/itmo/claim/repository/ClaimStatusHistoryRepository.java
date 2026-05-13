package blps.itmo.claim.repository;

import blps.itmo.claim.domain.ClaimStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClaimStatusHistoryRepository extends JpaRepository<ClaimStatusHistory, Integer> {

    List<ClaimStatusHistory> findByClaimIdOrderByCreatedAtAsc(Integer claimId);
}
