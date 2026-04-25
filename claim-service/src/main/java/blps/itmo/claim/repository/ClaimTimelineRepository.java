package blps.itmo.claim.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import blps.itmo.claim.domain.ClaimTimelineEntry;

public interface ClaimTimelineRepository extends JpaRepository<ClaimTimelineEntry, Long> {
    List<ClaimTimelineEntry> findByClaimIdOrderByCreatedAtAsc(Long claimId);
}
