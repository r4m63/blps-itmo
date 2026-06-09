package blps.itmo.auth.kafka;

import java.time.Instant;
import java.util.UUID;

// serialized JSON EventEnvelope -- this is value in kafka record
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        String sagaId,
        String correlationId,
        Instant occurredAt,
        T payload
) {
}
