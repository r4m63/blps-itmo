package blps.itmo.claim.controller;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import blps.itmo.claim.domain.ClaimTimelineEntry;
import blps.itmo.claim.service.ClaimProcessService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimProcessService claimProcessService;

    public ClaimController(ClaimProcessService claimProcessService) {
        this.claimProcessService = claimProcessService;
    }

    @PostMapping
    public ClaimResponse createClaim(
            @RequestHeader(value = "X-User-Id", required = false) Long actorUserId,
            @RequestBody CreateClaimRequest request) {
        return claimProcessService.createClaim(request, actorUserId);
    }

    @GetMapping("/{id}")
    public ClaimResponse getClaim(@PathVariable Long id) {
        return claimProcessService.getClaim(id);
    }

    @GetMapping("/{id}/timeline")
    public List<ClaimTimelineEntry> getTimeline(@PathVariable Long id) {
        return claimProcessService.getTimeline(id);
    }

    @GetMapping("/{id}/attachments")
    public List<ClaimAttachmentResponse> getAttachments(@PathVariable Long id) {
        return claimProcessService.getAttachments(id);
    }

    @GetMapping("/{id}/process-status")
    public ClaimProcessStatusResponse getProcessStatus(@PathVariable Long id) {
        return claimProcessService.getProcessStatus(id);
    }

    @PostMapping("/{id}/additional-info")
    public ClaimResponse additionalInfo(
            @RequestHeader(value = "X-User-Id", required = false) Long actorUserId,
            @PathVariable Long id,
            @RequestBody AdditionalInfoRequest request) {
        return claimProcessService.provideAdditionalInfo(id, request, actorUserId);
    }

    @PostMapping("/{id}/tenant-response")
    public ClaimResponse tenantResponse(
            @RequestHeader(value = "X-User-Id", required = false) Long actorUserId,
            @PathVariable Long id,
            @RequestBody TenantResponseRequest request) {
        return claimProcessService.submitTenantResponse(id, request, actorUserId);
    }

    @PostMapping("/{id}/support-decision")
    public ClaimResponse supportDecision(
            @RequestHeader(value = "X-User-Id", required = false) Long actorUserId,
            @PathVariable Long id,
            @RequestBody SupportDecisionRequest request) {
        return claimProcessService.supportDecision(id, request, actorUserId);
    }

    @PostMapping("/{id}/repair/reassess")
    public ClaimResponse repairReassess(
            @RequestHeader(value = "X-User-Id", required = false) Long actorUserId,
            @PathVariable Long id,
            @RequestBody(required = false) RepairRequest request) {
        return claimProcessService.repairReassess(id, actorUserId, request == null ? null : request.note());
    }

    @PostMapping("/{id}/repair/close")
    public ClaimResponse repairClose(
            @RequestHeader(value = "X-User-Id", required = false) Long actorUserId,
            @PathVariable Long id,
            @RequestBody(required = false) RepairRequest request) {
        return claimProcessService.repairClose(id, actorUserId, request == null ? null : request.note());
    }

    public record CreateClaimRequest(
            @NotNull Long landlordUserId,
            @NotNull Long tenantUserId,
            @NotBlank String title,
            @NotBlank String description,
            @NotNull BigDecimal claimedAmount,
            @NotBlank String currency,
            List<Long> attachmentIds) {
    }

    public record AdditionalInfoRequest(
            @NotNull Long landlordUserId,
            String comment) {
    }

    public record TenantResponseRequest(
            @NotNull Long tenantUserId,
            boolean agree,
            String comment) {
    }

    public record SupportDecisionRequest(
            @NotNull Long adminUserId,
            boolean applyPenalty,
            BigDecimal penaltyAmount,
            String penaltyCurrency,
            String note,
            boolean simulateFailure) {
    }

    public record ClaimResponse(
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
            List<ClaimAttachmentResponse> attachments) {
    }

    public record ClaimAttachmentResponse(
            Long attachmentId,
            String status,
            String failureReason) {
    }

    public record ClaimProcessStatusResponse(
            Long claimId,
            String status,
            String correlationId,
            boolean terminal,
            List<ClaimAttachmentResponse> attachments) {
    }

    public record RepairRequest(String note) {
    }
}
