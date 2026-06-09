package blps.itmo.claim.repository;

import blps.itmo.claim.domain.ClaimMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClaimMessageRepository extends JpaRepository<ClaimMessage, Integer> {

    List<ClaimMessage> findByClaimIdOrderByCreatedAtAsc(Integer claimId);
}
