package blps.itmo.platform.outbox.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Таблица для дедупликации сообщений из Kafka.
 *
 * Проблема: Kafka гарантирует at-least-once доставку, то есть
 * одно сообщение может прийти 1+ раз (например, при перебалансировке партиций).
 *
 * Решение: consumer перед обработкой проверяет, не обрабатывал ли он уже eventId
 *
 * Использование:
 * =============
 * @KafkaListener
 * public void handleEvent(EventEnvelope envelope) {
 *     if (processedMessageService.isProcessed(envelope.getEventId(), serviceName)) {
 *         return; // уже обработали, пропускаем
 *     }
 *
 *     // бизнес-логика...
 *
 *     processedMessageService.markProcessed(
 *         envelope.getEventId(),
 *         serviceName,
 *         envelope.getCorrelationId()
 *     );
 * }
 *
 * Важно: consumerName должен быть уникальным для каждого consumer-а,
 * чтобы разные сервисы не мешали друг другу
 */
@Entity
@Table(name = "processed_messages", uniqueConstraints = {
        @UniqueConstraint(name = "uk_processed_message", columnNames = {
                "event_id", "consumer_name"
                // одна пара (event_id, consumer_name) уникальна
        })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // суррогатный ключ

    @Column(name = "event_id", nullable = false)
    private String eventId; // ID события (из EventEnvelope)

    @Column(name = "consumer_name", nullable = false)
    private String consumerName; // кто обработал (например, "notification-service")

    @Column(name = "correlation_id", nullable = false)
    private String correlationId; // для трассировки (чей запрос привел к этому событию)

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt; // когда обработано
}
