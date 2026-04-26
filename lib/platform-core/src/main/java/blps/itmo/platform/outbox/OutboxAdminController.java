package blps.itmo.platform.outbox;

import java.time.Instant;
import java.util.Map;

import blps.itmo.platform.outbox.domain.OutboxEvent;
import blps.itmo.platform.outbox.domain.OutboxEventRepository;
import blps.itmo.platform.outbox.domain.OutboxStatus;
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

    /**
     * Возвращает статистику по всем outbox-событиям.
     * Статусы:
     * - NEW:       только что создано, еще не отправлено в Kafka
     * - PUBLISHED: успешно отправлено в Kafka
     * - FAILED:    ошибка при отправке, будет повторная попытка
     * - DEAD:      превышен лимит ретраев, требуется ручное вмешательство
     * Используется для мониторинга и отладки
     */
    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return Map.of(
                "new", outboxEventRepository.countByStatus(OutboxStatus.NEW),
                "published", outboxEventRepository.countByStatus(OutboxStatus.PUBLISHED),
                "failed", outboxEventRepository.countByStatus(OutboxStatus.FAILED),
                "dead", outboxEventRepository.countByStatus(OutboxStatus.DEAD));
    }

    /**
     * Ручной перезапуск умершего события.
     * Когда событие попадает в DEAD (ошибка не исправляется после N попыток),
     * администратор может:
     * 1. Исправить причину (например, поднять Kafka или исправить данные)
     * 2. Вызвать этот эндпоинт, чтобы сбросить статус в NEW
     * 3. OutboxRelay подхватит и отправит заново
     *
     * @param id ID события в БД
     * @return обновленное событие
     */
    @PostMapping("/{id}/replay")
    public OutboxEvent replay(@PathVariable Long id) {
        OutboxEvent event = outboxEventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Outbox event not found: " + id));
        // Нельзя переигрывать уже успешно отправленные события
        if (event.getStatus() == OutboxStatus.PUBLISHED) {
            throw new IllegalStateException("Published outbox events cannot be replayed from this endpoint");
        }
        // Сбрасываем статус в NEW, чтобы OutboxRelay обработал заново
        event.setStatus(OutboxStatus.NEW);
        event.setRetryCount(0);
        event.setErrorMessage("Replayed manually at " + Instant.now());
        return outboxEventRepository.save(event);
    }
}
