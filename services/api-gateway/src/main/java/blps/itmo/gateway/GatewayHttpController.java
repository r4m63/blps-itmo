package blps.itmo.gateway;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import blps.itmo.grpc.AdditionalInfoRequest;
import blps.itmo.grpc.ConfirmAttachmentRequest;
import blps.itmo.grpc.CreateClaimRequest;
import blps.itmo.grpc.DeactivateUserRequest;
import blps.itmo.grpc.EmptyRequest;
import blps.itmo.grpc.GetAttachmentRequest;
import blps.itmo.grpc.GetClaimEventsRequest;
import blps.itmo.grpc.GetClaimRequest;
import blps.itmo.grpc.GetUserRequest;
import blps.itmo.grpc.InitAttachmentRequest;
import blps.itmo.grpc.ListAttachmentsRequest;
import blps.itmo.grpc.ListMyClaimsRequest;
import blps.itmo.grpc.ListMyNotificationsRequest;
import blps.itmo.grpc.ListPenaltyOperationsRequest;
import blps.itmo.grpc.LoginRequest;
import blps.itmo.grpc.RepairClaimRequest;
import blps.itmo.grpc.RetryPenaltyOperationRequest;
import blps.itmo.grpc.SupportDecisionRequest;
import blps.itmo.grpc.TenantResponseRequest;
import blps.itmo.platform.grpc.GrpcMapping;
import jakarta.servlet.http.HttpServletRequest;

/**
 * ОСНОВНОЙ REST КОНТРОЛЛЕР GATEWAY
 *
 * Транслирует HTTP запросы клиентов в gRPC вызовы бекенд-сервисов.
 *
 * Паттерн работы:
 * ===============
 * 1. Получает HTTP запрос (JSON)
 * 2. Извлекает userId из атрибутов фильтра (уже проверенный JWT)
 * 3. Конвертирует HTTP DTO в gRPC Protobuf запрос
 * 4. Вызывает gRPC метод бекенд-сервиса
 * 5. Конвертирует gRPC Protobuf ответ в HTTP DTO (JSON)
 * 6. Возвращает клиенту
 *
 * Важно: все вызовы бекендов — через gRPC, даже если кажется,
 * что можно было бы вызвать через HTTP. Это сделано для единообразия
 * и производительности (Protobuf быстрее JSON).
 */
@RestController
@RequestMapping("/api")
public class GatewayHttpController {

    private final GatewayGrpcClients grpcClients;

    public GatewayHttpController(GatewayGrpcClients grpcClients) {
        this.grpcClients = grpcClients;
    }

    @PostMapping("/auth/login")
    public LoginHttpResponse login(@RequestBody LoginHttpRequest request) {
        var response = grpcClients.auth().login(LoginRequest.newBuilder()
                .setUserId(request.userId() == null ? 0 : request.userId())
                .setEmail(request.email() == null ? "" : request.email())
                .build());
        return new LoginHttpResponse(response.getToken(), toHttp(response.getUser()));
    }

    @GetMapping("/auth/users")
    public List<UserHttpResponse> listUsers() {
        return grpcClients.auth().listUsers(EmptyRequest.getDefaultInstance()).getUsersList().stream()
                .map(this::toHttp)
                .toList();
    }

    @PostMapping("/auth/users/{id}/deactivate")
    public UserHttpResponse deactivateUser(@PathVariable Long id,
            @RequestBody(required = false) DeactivateUserHttpRequest request,
            HttpServletRequest servletRequest) {
        var response = grpcClients.auth().deactivateUser(DeactivateUserRequest.newBuilder()
                .setUserId(id)
                .setReason(request == null || request.reason() == null ? "" : request.reason())
                .setActorRole(actorRole(servletRequest))
                .build());
        return toHttp(response);
    }

    @PostMapping("/claims")
    public ClaimHttpResponse createClaim(@RequestBody CreateClaimHttpRequest request, HttpServletRequest servletRequest) {
        CreateClaimRequest.Builder grpcRequest = CreateClaimRequest.newBuilder()
                .setActorUserId(actorUserId(servletRequest))
                .setLandlordUserId(request.landlordUserId() == null ? 0 : request.landlordUserId())
                .setTenantUserId(request.tenantUserId())
                .setTitle(request.title())
                .setDescription(request.description())
                .setClaimedAmount(money(request.claimedAmount()))
                .setCurrency(request.currency());
        if (request.attachmentIds() != null) {
            grpcRequest.addAllAttachmentIds(request.attachmentIds());
        }
        return toHttp(grpcClients.claim().createClaim(grpcRequest.build()));
    }

    @GetMapping("/claims/{id}")
    public ClaimHttpResponse getClaim(@PathVariable Long id) {
        return toHttp(grpcClients.claim().getClaim(GetClaimRequest.newBuilder().setClaimId(id).build()));
    }

    @GetMapping("/claims/{id}/timeline")
    public List<ClaimTimelineHttpResponse> getTimeline(@PathVariable Long id) {
        return grpcClients.claim().getTimeline(GetClaimRequest.newBuilder().setClaimId(id).build()).getEntriesList()
                .stream()
                .map(this::toHttp)
                .toList();
    }

    @GetMapping("/claims/{id}/attachments")
    public List<ClaimAttachmentHttpResponse> getClaimAttachments(@PathVariable Long id) {
        return grpcClients.claim().getAttachments(GetClaimRequest.newBuilder().setClaimId(id).build()).getAttachmentsList()
                .stream()
                .map(this::toHttp)
                .toList();
    }

    @GetMapping("/claims/{id}/process-status")
    public ClaimProcessStatusHttpResponse getProcessStatus(@PathVariable Long id) {
        var status = grpcClients.claim().getProcessStatus(GetClaimRequest.newBuilder().setClaimId(id).build());
        return new ClaimProcessStatusHttpResponse(
                status.getClaimId(),
                status.getStatus(),
                status.getCorrelationId(),
                status.getTerminal(),
                status.getAttachmentsList().stream().map(this::toHttp).toList());
    }

    @PostMapping("/claims/{id}/additional-info")
    public ClaimHttpResponse additionalInfo(@PathVariable Long id,
            @RequestBody AdditionalInfoHttpRequest request,
            HttpServletRequest servletRequest) {
        return toHttp(grpcClients.claim().provideAdditionalInfo(AdditionalInfoRequest.newBuilder()
                .setClaimId(id)
                .setActorUserId(actorUserId(servletRequest))
                .setLandlordUserId(request.landlordUserId() == null ? 0 : request.landlordUserId())
                .setComment(request.comment() == null ? "" : request.comment())
                .build()));
    }

    @PostMapping("/claims/{id}/tenant-response")
    public ClaimHttpResponse tenantResponse(@PathVariable Long id,
            @RequestBody TenantResponseHttpRequest request,
            HttpServletRequest servletRequest) {
        return toHttp(grpcClients.claim().submitTenantResponse(TenantResponseRequest.newBuilder()
                .setClaimId(id)
                .setActorUserId(actorUserId(servletRequest))
                .setTenantUserId(request.tenantUserId() == null ? 0 : request.tenantUserId())
                .setAgree(request.agree())
                .setComment(request.comment() == null ? "" : request.comment())
                .build()));
    }

    @PostMapping("/claims/{id}/support-decision")
    public ClaimHttpResponse supportDecision(@PathVariable Long id,
            @RequestBody SupportDecisionHttpRequest request,
            HttpServletRequest servletRequest) {
        return toHttp(grpcClients.claim().supportDecision(SupportDecisionRequest.newBuilder()
                .setClaimId(id)
                .setActorUserId(actorUserId(servletRequest))
                .setAdminUserId(request.adminUserId() == null ? 0 : request.adminUserId())
                .setApplyPenalty(request.applyPenalty())
                .setPenaltyAmount(money(request.penaltyAmount()))
                .setPenaltyCurrency(request.penaltyCurrency() == null ? "" : request.penaltyCurrency())
                .setNote(request.note() == null ? "" : request.note())
                .setSimulateFailure(request.simulateFailure())
                .build()));
    }

    @PostMapping("/claims/{id}/repair/reassess")
    public ClaimHttpResponse repairReassess(@PathVariable Long id,
            @RequestBody(required = false) RepairHttpRequest request,
            HttpServletRequest servletRequest) {
        return toHttp(grpcClients.claim().repairReassess(repairRequest(id, request, servletRequest)));
    }

    @PostMapping("/claims/{id}/repair/close")
    public ClaimHttpResponse repairClose(@PathVariable Long id,
            @RequestBody(required = false) RepairHttpRequest request,
            HttpServletRequest servletRequest) {
        return toHttp(grpcClients.claim().repairClose(repairRequest(id, request, servletRequest)));
    }

    @GetMapping("/penalties/claims/{claimId}")
    public List<PenaltyOperationHttpResponse> getPenaltyOperations(@PathVariable Long claimId) {
        return grpcClients.penalty().listOperationsByClaim(ListPenaltyOperationsRequest.newBuilder()
                        .setClaimId(claimId)
                        .build())
                .getOperationsList()
                .stream()
                .map(this::toHttp)
                .toList();
    }

    @PostMapping("/penalties/operations/{operationId}/retry")
    public PenaltyOperationHttpResponse retryPenalty(@PathVariable Long operationId, HttpServletRequest servletRequest) {
        return toHttp(grpcClients.penalty().retryOperation(RetryPenaltyOperationRequest.newBuilder()
                .setOperationId(operationId)
                .setActorRole(actorRole(servletRequest))
                .build()));
    }

    @PostMapping("/attachments/init")
    public AttachmentHttpResponse initAttachment(@RequestBody InitAttachmentHttpRequest request,
            HttpServletRequest servletRequest) {
        return toHttp(grpcClients.storage().initAttachment(InitAttachmentRequest.newBuilder()
                .setActorUserId(actorUserId(servletRequest))
                .setOwnerUserId(request.ownerUserId() == null ? 0 : request.ownerUserId())
                .setOriginalFilename(request.originalFilename())
                .setContentType(request.contentType() == null ? "" : request.contentType())
                .build()));
    }

    @PostMapping("/attachments/{id}/confirm")
    public AttachmentHttpResponse confirmAttachment(@PathVariable Long id,
            @RequestBody(required = false) ConfirmAttachmentHttpRequest request,
            HttpServletRequest servletRequest) {
        return toHttp(grpcClients.storage().confirmAttachment(ConfirmAttachmentRequest.newBuilder()
                .setAttachmentId(id)
                .setActorUserId(actorUserId(servletRequest))
                .setObjectKey(request == null || request.objectKey() == null ? "" : request.objectKey())
                .build()));
    }

    @GetMapping("/attachments/{id}")
    public AttachmentHttpResponse getAttachment(@PathVariable Long id) {
        return toHttp(grpcClients.storage().getAttachment(GetAttachmentRequest.newBuilder().setAttachmentId(id).build()));
    }

    @GetMapping("/attachments")
    public List<AttachmentHttpResponse> listAttachments(HttpServletRequest servletRequest) {
        return grpcClients.storage().listAttachments(ListAttachmentsRequest.newBuilder()
                        .setOwnerUserId(actorUserId(servletRequest))
                        .build())
                .getAttachmentsList()
                .stream()
                .map(this::toHttp)
                .toList();
    }

    @GetMapping("/claims/my")
    public List<ClaimHttpResponse> listMyClaims(HttpServletRequest servletRequest) {
        return grpcClients.claim().listMyClaims(ListMyClaimsRequest.newBuilder()
                        .setActorUserId(actorUserId(servletRequest))
                        .build())
                .getClaimsList()
                .stream()
                .map(this::toHttp)
                .toList();
    }

    @GetMapping("/notifications/my")
    public List<NotificationHttpResponse> listMyNotifications(HttpServletRequest servletRequest) {
        return grpcClients.notification().listMyNotifications(ListMyNotificationsRequest.newBuilder()
                        .setUserId(actorUserId(servletRequest))
                        .setLimit(20)
                        .build())
                .getNotificationsList()
                .stream()
                .map(this::toHttp)
                .toList();
    }

    @GetMapping("/audit/claims/{claimId}/events")
    public List<AuditRecordHttpResponse> getClaimEvents(@PathVariable Long claimId) {
        return grpcClients.audit().getClaimEvents(GetClaimEventsRequest.newBuilder().setClaimId(claimId).build())
                .getRecordsList()
                .stream()
                .map(this::toHttp)
                .toList();
    }

    private RepairClaimRequest repairRequest(Long id, RepairHttpRequest request, HttpServletRequest servletRequest) {
        return RepairClaimRequest.newBuilder()
                .setClaimId(id)
                .setActorUserId(actorUserId(servletRequest))
                .setNote(request == null || request.note() == null ? "" : request.note())
                .build();
    }

    private long actorUserId(HttpServletRequest request) {
        Object value = request.getAttribute(GatewayAuthFilter.ATTR_USER_ID);
        return value == null ? 0 : Long.parseLong(value.toString());
    }

    private String actorRole(HttpServletRequest request) {
        Object value = request.getAttribute(GatewayAuthFilter.ATTR_USER_ROLE);
        return value == null ? "" : value.toString();
    }

    private String money(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private UserHttpResponse toHttp(blps.itmo.grpc.UserDto user) {
        return new UserHttpResponse(user.getId(), user.getEmail(), user.getRole(), user.getEnabled(),
                user.getPenaltyCount());
    }

    private ClaimHttpResponse toHttp(blps.itmo.grpc.ClaimDto claim) {
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
                claim.getAttachmentsList().stream().map(this::toHttp).toList());
    }

    private ClaimAttachmentHttpResponse toHttp(blps.itmo.grpc.AttachmentRefDto attachment) {
        return new ClaimAttachmentHttpResponse(
                attachment.getAttachmentId(),
                attachment.getStatus(),
                attachment.getFailureReason().isBlank() ? null : attachment.getFailureReason());
    }

    private ClaimTimelineHttpResponse toHttp(blps.itmo.grpc.ClaimTimelineEntryDto entry) {
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

    private PenaltyOperationHttpResponse toHttp(blps.itmo.grpc.PenaltyOperationDto operation) {
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

    private AttachmentHttpResponse toHttp(blps.itmo.grpc.AttachmentDto attachment) {
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

    private AuditRecordHttpResponse toHttp(blps.itmo.grpc.AuditRecordDto record) {
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

    private NotificationHttpResponse toHttp(blps.itmo.grpc.NotificationRecordDto n) {
        return new NotificationHttpResponse(
                n.getId(),
                n.getEventType(),
                n.getAggregateId(),
                n.getRecipientUserId() == 0 ? null : n.getRecipientUserId(),
                n.getChannel(),
                n.getTemplateKey(),
                n.getSubject().isBlank() ? null : n.getSubject(),
                n.getMessage(),
                GrpcMapping.offsetDateTime(n.getCreatedAt()));
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
            Long landlordUserId,
            Long tenantUserId,
            String title,
            String description,
            BigDecimal claimedAmount,
            String currency,
            List<Long> attachmentIds) {
    }

    public record AdditionalInfoHttpRequest(Long landlordUserId, String comment) {
    }

    public record TenantResponseHttpRequest(Long tenantUserId, boolean agree, String comment) {
    }

    public record SupportDecisionHttpRequest(
            Long adminUserId,
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

    public record InitAttachmentHttpRequest(Long ownerUserId, String originalFilename, String contentType) {
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
