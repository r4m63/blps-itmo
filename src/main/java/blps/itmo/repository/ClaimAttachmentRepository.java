package blps.itmo.repository;

import blps.itmo.entity.ClaimAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimAttachmentRepository extends JpaRepository<ClaimAttachment, Long> {
    List<ClaimAttachment> findByClaimId(Long claimId);
    List<ClaimAttachment> findByMessageId(Long messageId);
    List<ClaimAttachment> findByObjectKeyIn(List<String> keys);
    java.util.Optional<ClaimAttachment> findByObjectKey(String key);
}
