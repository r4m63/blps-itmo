package blps.itmo.platform.persistence;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/outbox")
public class OutboxAdminController {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxAdminController(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return Map.of(
                "new", outboxEventRepository.countByStatus(OutboxStatus.NEW),
                "published", outboxEventRepository.countByStatus(OutboxStatus.PUBLISHED),
                "failed", outboxEventRepository.countByStatus(OutboxStatus.FAILED),
                "dead", outboxEventRepository.countByStatus(OutboxStatus.DEAD));
    }

    @PostMapping("/{id}/replay")
    public OutboxEvent replay(@PathVariable Long id) {
        OutboxEvent event = outboxEventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Outbox event not found: " + id));
        if (event.getStatus() == OutboxStatus.PUBLISHED) {
            throw new IllegalStateException("Published outbox events cannot be replayed from this endpoint");
        }
        event.setStatus(OutboxStatus.NEW);
        event.setRetryCount(0);
        event.setErrorMessage("Replayed manually at " + Instant.now());
        return outboxEventRepository.save(event);
    }
}
