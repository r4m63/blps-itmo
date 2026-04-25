package blps.itmo.claim.controller;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

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
    public ClaimResponse createClaim(@RequestBody CreateClaimRequest request) {
        return claimProcessService.createClaim(request);
    }

    @GetMapping("/{id}")
    public ClaimResponse getClaim(@PathVariable Long id) {
        return claimProcessService.getClaim(id);
    }

    @GetMapping("/{id}/timeline")
    public List<ClaimTimelineEntry> getTimeline(@PathVariable Long id) {
        return claimProcessService.getTimeline(id);
    }

    @PostMapping("/{id}/additional-info")
    public ClaimResponse additionalInfo(@PathVariable Long id, @RequestBody AdditionalInfoRequest request) {
        return claimProcessService.provideAdditionalInfo(id, request);
    }

    @PostMapping("/{id}/tenant-response")
    public ClaimResponse tenantResponse(@PathVariable Long id, @RequestBody TenantResponseRequest request) {
        return claimProcessService.submitTenantResponse(id, request);
    }

    @PostMapping("/{id}/support-decision")
    public ClaimResponse supportDecision(@PathVariable Long id, @RequestBody SupportDecisionRequest request) {
        return claimProcessService.supportDecision(id, request);
    }

    public record CreateClaimRequest(
            @NotNull Long landlordUserId,
            @NotNull Long tenantUserId,
            @NotBlank String title,
            @NotBlank String description,
            @NotNull BigDecimal claimedAmount,
            @NotBlank String currency) {
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
            OffsetDateTime closedAt) {
    }
}
