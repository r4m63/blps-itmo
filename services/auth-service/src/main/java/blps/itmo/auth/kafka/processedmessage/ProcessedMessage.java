package blps.itmo.auth.kafka.processedmessage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_messages")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedMessage {
    // что получили
    // Kafka гарантирует at-least-once delivery. То есть могут быть дубли одного и того же сообщения.

    // INBOX
    // запоминает uuid каждого обработанного сообщения, если пришел повторно - игнор
    @Id
    @Column(name = "event_id")
    private UUID eventId; // тот же что и outbox_events.id на стороне отправителя

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();
}
