package blps.itmo.claim.api.dto;

import blps.itmo.claim.domain.ClaimStatus;
import blps.itmo.claim.domain.ClaimStatusHistory;

import java.time.Instant;

public record ClaimStatusHistoryResponse(
        Integer id,
        Integer claimId,
        ClaimStatus fromStatus,
        ClaimStatus toStatus,
        Integer actorId,
        String note,
        Instant createdAt
) {
    public static ClaimStatusHistoryResponse from(ClaimStatusHistory h) {
        return new ClaimStatusHistoryResponse(
                h.getId(),
                h.getClaimId(),
                h.getFromStatus(),
                h.getToStatus(),
                h.getActorId(),
                h.getNote(),
                h.getCreatedAt()
        );
    }
}
