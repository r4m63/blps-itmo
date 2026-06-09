package blps.itmo.claim.api.dto;

import blps.itmo.claim.domain.ClaimMessage;
import blps.itmo.claim.domain.CommentType;

import java.time.Instant;

public record ClaimMessageResponse(
        Integer id,
        Integer claimId,
        Integer userId,
        CommentType messageType,
        String body,
        Instant createdAt
) {
    public static ClaimMessageResponse from(ClaimMessage m) {
        return new ClaimMessageResponse(
                m.getId(),
                m.getClaimId(),
                m.getUserId(),
                m.getMessageType(),
                m.getBody(),
                m.getCreatedAt()
        );
    }
}
