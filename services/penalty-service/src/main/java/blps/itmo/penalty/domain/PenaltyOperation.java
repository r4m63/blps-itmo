package blps.itmo.penalty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "penalty_operations")
@Getter
@Setter
@NoArgsConstructor
public class PenaltyOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "claim_id", nullable = false, unique = true)
    private Integer claimId;

    @Column(name = "tenant_id", nullable = false)
    private Integer tenantId;

    @Column(name = "landlord_id", nullable = false)
    private Integer landlordId;

    @Column(name = "requested_by", nullable = false)
    private Integer requestedBy;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "penaltystatus")
    private PenaltyStatus status = PenaltyStatus.REQUESTED;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
