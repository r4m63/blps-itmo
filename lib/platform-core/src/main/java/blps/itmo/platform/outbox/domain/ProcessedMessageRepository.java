package blps.itmo.platform.outbox.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, Long> {
    boolean existsByEventIdAndConsumerName(String eventId, String consumerName);
}
