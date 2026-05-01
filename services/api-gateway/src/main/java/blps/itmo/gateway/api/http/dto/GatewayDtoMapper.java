package blps.itmo.gateway.api.http.dto;

import blps.itmo.gateway.dto.GatewayDtos.AttachmentHttpResponse;
import blps.itmo.gateway.dto.GatewayDtos.AuditRecordHttpResponse;
import blps.itmo.gateway.dto.GatewayDtos.ClaimAttachmentHttpResponse;
import blps.itmo.gateway.dto.GatewayDtos.ClaimHttpResponse;
import blps.itmo.gateway.dto.GatewayDtos.ClaimTimelineHttpResponse;
import blps.itmo.gateway.dto.GatewayDtos.NotificationHttpResponse;
import blps.itmo.gateway.dto.GatewayDtos.PenaltyOperationHttpResponse;
import blps.itmo.gateway.dto.GatewayDtos.UserHttpResponse;
import blps.itmo.platform.grpc.GrpcMapping;

public final class GatewayDtoMapper {

    private GatewayDtoMapper() {
    }

    public static UserHttpResponse toHttp(blps.itmo.grpc.UserDto user) {
        return new UserHttpResponse(user.getId(), user.getEmail(), user.getRole(), user.getEnabled(),
                user.getPenaltyCount());
    }

    public static ClaimHttpResponse toHttp(blps.itmo.grpc.ClaimDto claim) {
        return new ClaimHttpResponse(
                claim.getId(),
                claim.getCorrelationId(),
                claim.getLandlordId(),
                claim.getTenantId(),
                claim.getStatus(),
                claim.getTitle(),
                claim.getDescription(),
                GrpcMapping.money(claim.getClaimedAmount()),
                claim.getCurrency(),
                GrpcMapping.money(claim.getAssessmentAmount()),
                claim.getAssessmentNotes().isBlank() ? null : claim.getAssessmentNotes(),
                GrpcMapping.money(claim.getPenaltyAmount()),
                claim.getPenaltyCurrency().isBlank() ? null : claim.getPenaltyCurrency(),
                claim.getResolutionNote().isBlank() ? null : claim.getResolutionNote(),
                GrpcMapping.offsetDateTime(claim.getCreatedAt()),
                GrpcMapping.offsetDateTime(claim.getUpdatedAt()),
                GrpcMapping.offsetDateTime(claim.getClosedAt()),
                claim.getTenantAgreedValue().isEmpty() ? null : Boolean.parseBoolean(claim.getTenantAgreedValue()),
                claim.getAttachmentsList().stream().map(GatewayDtoMapper::toHttp).toList());
    }

    public static ClaimAttachmentHttpResponse toHttp(blps.itmo.grpc.AttachmentRefDto attachment) {
        return new ClaimAttachmentHttpResponse(
                attachment.getAttachmentId(),
                attachment.getStatus(),
                attachment.getFailureReason().isBlank() ? null : attachment.getFailureReason());
    }

    public static ClaimTimelineHttpResponse toHttp(blps.itmo.grpc.ClaimTimelineEntryDto entry) {
        return new ClaimTimelineHttpResponse(
                entry.getId(),
                entry.getClaimId(),
                entry.getEventType(),
                entry.getFromStatus().isBlank() ? null : entry.getFromStatus(),
                entry.getToStatus().isBlank() ? null : entry.getToStatus(),
                entry.getActorId() == 0 ? null : entry.getActorId(),
                entry.getNote().isBlank() ? null : entry.getNote(),
                GrpcMapping.offsetDateTime(entry.getCreatedAt()));
    }

    public static PenaltyOperationHttpResponse toHttp(blps.itmo.grpc.PenaltyOperationDto operation) {
        return new PenaltyOperationHttpResponse(
                operation.getId(),
                operation.getClaimId(),
                operation.getTenantId(),
                operation.getCorrelationId(),
                GrpcMapping.money(operation.getPenaltyAmount()),
                operation.getPenaltyCurrency(),
                operation.getSimulateFailure(),
                operation.getReason().isBlank() ? null : operation.getReason(),
                operation.getStatus(),
                GrpcMapping.offsetDateTime(operation.getCreatedAt()),
                GrpcMapping.offsetDateTime(operation.getProcessedAt()));
    }

    public static AttachmentHttpResponse toHttp(blps.itmo.grpc.AttachmentDto attachment) {
        return new AttachmentHttpResponse(
                attachment.getId(),
                attachment.getOwnerUserId(),
                attachment.getClaimId() == 0 ? null : attachment.getClaimId(),
                attachment.getBucketName(),
                attachment.getObjectKey(),
                attachment.getUploadUrl(),
                attachment.getOriginalFilename(),
                attachment.getContentType().isBlank() ? null : attachment.getContentType(),
                attachment.getStatus(),
                attachment.getFailureReason().isBlank() ? null : attachment.getFailureReason(),
                GrpcMapping.offsetDateTime(attachment.getCreatedAt()),
                GrpcMapping.offsetDateTime(attachment.getUpdatedAt()));
    }

    public static AuditRecordHttpResponse toHttp(blps.itmo.grpc.AuditRecordDto record) {
        return new AuditRecordHttpResponse(
                record.getId(),
                record.getEventId(),
                record.getEventType(),
                record.getAggregateType(),
                record.getAggregateId(),
                record.getCorrelationId(),
                record.getSagaId(),
                record.getPayloadJson(),
                GrpcMapping.offsetDateTime(record.getCreatedAt()));
    }

    public static NotificationHttpResponse toHttp(blps.itmo.grpc.NotificationRecordDto notification) {
        return new NotificationHttpResponse(
                notification.getId(),
                notification.getEventType(),
                notification.getAggregateId(),
                notification.getRecipientUserId() == 0 ? null : notification.getRecipientUserId(),
                notification.getChannel(),
                notification.getTemplateKey(),
                notification.getSubject().isBlank() ? null : notification.getSubject(),
                notification.getMessage(),
                GrpcMapping.offsetDateTime(notification.getCreatedAt()));
    }
}
