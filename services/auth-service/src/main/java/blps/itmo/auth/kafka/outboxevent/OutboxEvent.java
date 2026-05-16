package blps.itmo.auth.kafka.outboxevent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    private UUID id; // EventEnvelope.eventId

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType; // "claim", "penalty", "user" - для логов / отладки / Kafka UI фильтров

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId; // Kafka key

    @Column(name = "event_type", nullable = false)
    private String eventType; // envelope.eventType - тип события

    @Column(name = "topic", nullable = false)
    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload; // payload, сериализованный в JSON

    @Column(name = "status", nullable = false)
    private String status = "NEW"; // 'NEW' или 'PUBLISHED' для outbox relay

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0; // Сколько раз Relay пытался отправить и упал

    @Column(name = "last_error")
    private String lastError; // Последнее исключение при попытке отправки

    @Column(name = "saga_id")
    private UUID sagaId; // OutboxRelay перенесёт это в envelope.sagaId

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt; // Когда Relay отправил в Kafka
}
