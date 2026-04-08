package blps.itmo.repository.business;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import blps.itmo.entity.business.ClaimAttachment;

public interface ClaimAttachmentRepository extends JpaRepository<ClaimAttachment, Long> {

    List<ClaimAttachment> findByClaimId(Long claimId);

    List<ClaimAttachment> findByMessageId(Long messageId);

    List<ClaimAttachment> findByClaimIdIn(List<Long> claimIds);

    Optional<ClaimAttachment> findByObjectKey(String objectKey);

    List<ClaimAttachment> findByObjectKeyIn(List<String> objectKeys);

    List<ClaimAttachment> findByMessageIdIn(List<Long> messageIds);
}
