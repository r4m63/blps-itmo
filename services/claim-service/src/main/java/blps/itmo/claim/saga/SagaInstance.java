package blps.itmo.claim.saga;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_instances")
@Getter
@Setter
@NoArgsConstructor
public class SagaInstance {

    @Id
    @Column(name = "saga_id")
    private UUID sagaId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "saga_type", nullable = false, columnDefinition = "sagatype")
    private SagaType sagaType = SagaType.PENALTY_APPLICATION;

    @Column(name = "claim_id", nullable = false)
    private Integer claimId; // На какой claim навешана

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "state", nullable = false, columnDefinition = "sagastate")
    private SagaState state; // Текущая позиция в state машине

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0; // Счётчик ретраев компенсации

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
