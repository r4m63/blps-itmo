package blps.itmo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import blps.itmo.entity.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
}
