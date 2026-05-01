package blps.itmo.gateway.api.http.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public final class GatewayDtos {

    private GatewayDtos() {
    }

    public record LoginHttpRequest(Long userId, String email) {
    }

    public record LoginHttpResponse(String token, UserHttpResponse user) {
    }

    public record UserHttpResponse(Long id, String email, String role, boolean enabled, int penaltyCount) {
    }

    public record DeactivateUserHttpRequest(String reason) {
    }

    public record CreateClaimHttpRequest(
            @NotNull @Positive Long tenantUserId,
            @NotBlank String title,
            @NotBlank String description,
            @NotNull @PositiveOrZero BigDecimal claimedAmount,
            @NotBlank String currency,
            List<Long> attachmentIds) {
    }

    public record AdditionalInfoHttpRequest(@NotBlank String comment) {
    }

    public record TenantResponseHttpRequest(boolean agree, String comment) {
    }

    public record SupportDecisionHttpRequest(
            boolean applyPenalty,
            BigDecimal penaltyAmount,
            String penaltyCurrency,
            String note,
            boolean simulateFailure) {
    }

    public record RepairHttpRequest(String note) {
    }

    public record ClaimHttpResponse(
            Long id,
            String correlationId,
            Long landlordId,
            Long tenantId,
            String status,
            String title,
            String description,
            BigDecimal claimedAmount,
            String currency,
            BigDecimal assessmentAmount,
            String assessmentNotes,
            BigDecimal penaltyAmount,
            String penaltyCurrency,
            String resolutionNote,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime closedAt,
            Boolean tenantAgreed,
            List<ClaimAttachmentHttpResponse> attachments) {
    }

    public record ClaimAttachmentHttpResponse(Long attachmentId, String status, String failureReason) {
    }

    public record ClaimTimelineHttpResponse(
            Long id,
            Long claimId,
            String eventType,
            String fromStatus,
            String toStatus,
            Long actorId,
            String note,
            OffsetDateTime createdAt) {
    }

    public record ClaimProcessStatusHttpResponse(
            Long claimId,
            String status,
            String correlationId,
            boolean terminal,
            List<ClaimAttachmentHttpResponse> attachments) {
    }

    public record PenaltyOperationHttpResponse(
            Long id,
            Long claimId,
            Long tenantId,
            String correlationId,
            BigDecimal penaltyAmount,
            String penaltyCurrency,
            boolean simulateFailure,
            String reason,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime processedAt) {
    }

    public record InitAttachmentHttpRequest(@NotBlank String originalFilename, String contentType) {
    }

    public record ConfirmAttachmentHttpRequest(String objectKey) {
    }

    public record AttachmentHttpResponse(
            Long id,
            Long ownerUserId,
            Long claimId,
            String bucketName,
            String objectKey,
            String uploadUrl,
            String originalFilename,
            String contentType,
            String status,
            String failureReason,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record AuditRecordHttpResponse(
            Long id,
            String eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            String correlationId,
            String sagaId,
            String payloadJson,
            OffsetDateTime createdAt) {
    }

    public record NotificationHttpResponse(
            Long id,
            String eventType,
            String aggregateId,
            Long recipientUserId,
            String channel,
            String templateKey,
            String subject,
            String message,
            OffsetDateTime createdAt) {
    }
}
