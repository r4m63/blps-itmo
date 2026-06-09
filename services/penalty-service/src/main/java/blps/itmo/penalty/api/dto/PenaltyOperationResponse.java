package blps.itmo.penalty.api.dto;

import blps.itmo.penalty.domain.PenaltyOperation;
import blps.itmo.penalty.domain.PenaltyStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PenaltyOperationResponse(
        Integer id,
        Integer claimId,
        Integer tenantId,
        Integer landlordId,
        Integer requestedBy,
        BigDecimal amount,
        String currency,
        PenaltyStatus status,
        String failureReason,
        Instant appliedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static PenaltyOperationResponse from(PenaltyOperation p) {
        return new PenaltyOperationResponse(
                p.getId(),
                p.getClaimId(),
                p.getTenantId(),
                p.getLandlordId(),
                p.getRequestedBy(),
                p.getAmount(),
                p.getCurrency(),
                p.getStatus(),
                p.getFailureReason(),
                p.getAppliedAt(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
