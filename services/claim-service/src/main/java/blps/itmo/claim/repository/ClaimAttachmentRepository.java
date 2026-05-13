package blps.itmo.claim.repository;

import blps.itmo.claim.domain.ClaimAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClaimAttachmentRepository extends JpaRepository<ClaimAttachment, Integer> {

    List<ClaimAttachment> findByClaimId(Integer claimId);

    List<ClaimAttachment> findByMessageId(Integer messageId);

    Optional<ClaimAttachment> findByObjectKey(String objectKey);
}
