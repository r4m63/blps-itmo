package blps.itmo.claim.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import blps.itmo.claim.domain.ClaimAttachment;

public interface ClaimAttachmentRepository extends JpaRepository<ClaimAttachment, Long> {
    List<ClaimAttachment> findByClaimIdOrderByCreatedAtAsc(Long claimId);

    List<ClaimAttachment> findByClaimIdAndAttachmentIdIn(Long claimId, Collection<Long> attachmentIds);
}
