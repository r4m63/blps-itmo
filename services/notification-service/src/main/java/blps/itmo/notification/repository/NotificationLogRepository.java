package blps.itmo.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import blps.itmo.notification.domain.NotificationLog;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
}
