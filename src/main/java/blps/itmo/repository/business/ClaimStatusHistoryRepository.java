package blps.itmo.repository.business;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import blps.itmo.entity.business.ClaimStatusHistory;

public interface ClaimStatusHistoryRepository extends JpaRepository<ClaimStatusHistory, Long> {

    List<ClaimStatusHistory> findByClaimIdOrderByCreatedAtAsc(Long claimId);
}
