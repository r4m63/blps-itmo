package blps.itmo.storage.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import blps.itmo.storage.domain.Attachment;
import blps.itmo.storage.domain.AttachmentStatus;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByIdIn(Collection<Long> ids);

    List<Attachment> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

    List<Attachment> findByStatusAndUpdatedAtBefore(AttachmentStatus status, OffsetDateTime updatedAt);
}
