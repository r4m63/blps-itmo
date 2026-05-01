package blps.itmo.notification.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import blps.itmo.notification.domain.NotificationLog;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId, Pageable pageable);
}
