package blps.itmo.platform.outbox.domain;

import java.time.Instant;

import blps.itmo.platform.events.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Table(name = "outbox_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_outbox_event_id", columnNames = "event_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // суррогатный ключ, чисто для БД

    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId; // уникальный ID события (UUID), для дедупликации

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;  // тип события

    @Column(name = "topic_name", nullable = false)
    private String topicName; // в какой топик Kafka отправлять

    @Column(name = "event_key", nullable = false)
    private String eventKey; // ключ для партиционирования в Kafka

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType; // тип агрегата (например, "Claim")

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId; // ID агрегата (например, ID заявки)

    @Column(name = "correlation_id", nullable = false)
    private String correlationId; // сквозной ID для трассировки цепочки запросов

    @Column(name = "saga_id", nullable = false)
    private String sagaId; // ID саги (если событие часть распределенной транзакции)

    @Column(name = "actor_id")
    private Long actorId; // ID пользователя, который инициировал действие

    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson; // тело события в JSON (данные, которые уйдут в Kafka)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.NEW; // статус обработки

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0; // сколько раз пытались отправить

    @Column(name = "error_message")
    private String errorMessage; // последняя ошибка (если была)

    @Column(name = "created_at", nullable = false)
    private Instant createdAt; // когда событие создано

    @Column(name = "published_at")
    private Instant publishedAt;  // когда успешно отправлено
}
