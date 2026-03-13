package blps.itmo.repository;

import blps.itmo.entity.ClaimMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimMessageRepository extends JpaRepository<ClaimMessage, Long> {
    List<ClaimMessage> findByClaimIdOrderByCreatedAtAsc(Long claimId);
}
