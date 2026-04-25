package blps.itmo.platform.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findTop100ByStatusInOrderByCreatedAtAsc(Collection<OutboxStatus> statuses);
}
