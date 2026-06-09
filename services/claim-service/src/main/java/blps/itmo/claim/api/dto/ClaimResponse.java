package blps.itmo.claim.api.dto;

import blps.itmo.claim.domain.Claim;
import blps.itmo.claim.domain.ClaimStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClaimResponse(
        Integer id,
        Integer landlordId,
        Integer tenantId,
        ClaimStatus status,
        String title,
        String description,
        BigDecimal claimedAmount,
        String currency,
        BigDecimal assessmentAmount,
        String assessmentNotes,
        Integer adminReviewerId,
        String resolutionNote,
        Instant decidedAt,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt,
        UUID currentSagaId
) {
    public static ClaimResponse from(Claim c) {
        return new ClaimResponse(
                c.getId(),
                c.getLandlordId(),
                c.getTenantId(),
                c.getStatus(),
                c.getTitle(),
                c.getDescription(),
                c.getClaimedAmount(),
                c.getCurrency(),
                c.getAssessmentAmount(),
                c.getAssessmentNotes(),
                c.getAdminReviewerId(),
                c.getResolutionNote(),
                c.getDecidedAt(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getClosedAt(),
                c.getCurrentSagaId()
        );
    }
}
