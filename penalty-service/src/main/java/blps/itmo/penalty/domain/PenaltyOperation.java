package blps.itmo.penalty.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "penalty_operations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PenaltyOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "penalty_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal penaltyAmount;

    @Column(name = "penalty_currency", nullable = false, length = 3)
    private String penaltyCurrency;

    @Column(name = "simulate_failure", nullable = false)
    @Builder.Default
    private boolean simulateFailure = false;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PenaltyOperationStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;
}
