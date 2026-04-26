package blps.itmo.platform.outbox.domain;

/**
 * Статусы outbox-события.
 * Машина состояний:
 *   NEW ──(успех)──> PUBLISHED (конечное)
 *     │
 *     └──(ошибка)──> FAILED ──(ретрай)──> NEW (если retryCount < max)
 *                    │
 *                    └──(max retry)──> DEAD (конечное, требует ручного вмешательства)
 * - NEW:      событие создано, ждет отправки
 * - PUBLISHED: успешно отправлено в Kafka
 * - FAILED:   ошибка при отправке, будет повторная попытка
 * - DEAD:     превышен лимит ретраев, нужен admin через /internal/outbox/{id}/replay
 */
public enum OutboxStatus {
    NEW,
    PUBLISHED,
    FAILED,
    DEAD
}
