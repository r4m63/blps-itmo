package blps.itmo.repository.business;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import blps.itmo.entity.business.ClaimMessage;
import blps.itmo.entity.business.CommentType;

public interface ClaimMessageRepository extends JpaRepository<ClaimMessage, Long> {

    List<ClaimMessage> findByClaimIdOrderByCreatedAtAsc(Long claimId);

    List<ClaimMessage> findByClaimIdAndMessageTypeOrderByCreatedAtAsc(Long claimId, CommentType messageType);
}
