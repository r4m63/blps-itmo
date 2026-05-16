package blps.itmo.penalty.kafka;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        String sagaId,
        String correlationId,
        Instant occurredAt,
        T payload
) {
}
