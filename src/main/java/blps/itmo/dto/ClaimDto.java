package blps.itmo.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import blps.itmo.entity.Claim;
import blps.itmo.entity.ClaimStatus;

public record ClaimDto(
        Long id,
        String initiatorId,
        String respondentId,
        BigDecimal amount,
        String currency,
        String reason,
        ClaimStatus status,
        Boolean enoughData,
        Boolean groundsForPenalty,
        Boolean approvePenalty,
        Boolean penaltyApplied,
        Boolean respondentTimeout,
        String respondentComment,
        String supportComment,
        OffsetDateTime responseDueAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<AttachmentDto> attachments) {
    public static ClaimDto from(Claim claim) {
        return new ClaimDto(
                claim.getId(),
                claim.getInitiatorId(),
                claim.getRespondentId(),
                claim.getAmount(),
                claim.getCurrency(),
                claim.getReason(),
                claim.getStatus(),
                claim.getEnoughData(),
                claim.getGroundsForPenalty(),
                claim.getApprovePenalty(),
                claim.getPenaltyApplied(),
                claim.getRespondentTimeout(),
                claim.getRespondentComment(),
                claim.getSupportComment(),
                claim.getResponseDueAt(),
                claim.getCreatedAt(),
                claim.getUpdatedAt(),
                claim.getAttachments().stream().map(AttachmentDto::from).toList());
    }
}
