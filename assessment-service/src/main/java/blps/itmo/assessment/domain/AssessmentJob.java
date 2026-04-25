package blps.itmo.assessment.domain;

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
@Table(name = "assessment_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssessmentJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "landlord_id")
    private Long landlordId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "claimed_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal claimedAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "comment")
    private String comment;

    @Column(name = "attempt_no", nullable = false)
    @Builder.Default
    private int attemptNo = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssessmentJobStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;
}
