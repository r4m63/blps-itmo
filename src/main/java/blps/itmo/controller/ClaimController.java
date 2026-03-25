package blps.itmo.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import blps.itmo.dto.AdditionalInfoReplyRequest;
import blps.itmo.dto.AssessmentRequest;
import blps.itmo.dto.ClaimResponse;
import blps.itmo.dto.CreateClaimRequest;
import blps.itmo.dto.IntakeDecisionRequest;
import blps.itmo.dto.SupportDecisionRequest;
import blps.itmo.dto.TenantResponseRequest;
import blps.itmo.service.ClaimService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/claims")
@Validated
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    public ClaimResponse createClaim(@Valid @RequestBody CreateClaimRequest request) {
        return claimService.createClaim(request);
    }

    @PostMapping("/{id}/intake")
    public ClaimResponse intakeDecision(@PathVariable Long id,
            @Valid @RequestBody IntakeDecisionRequest request) {
        return claimService.intakeDecision(id, request);
    }

    @PostMapping("/{id}/additional-info")
    public ClaimResponse submitAdditionalInfo(@PathVariable Long id,
            @Valid @RequestBody AdditionalInfoReplyRequest request) {
        return claimService.additionalInfoReply(id, request);
    }

    @PostMapping("/{id}/assessment")
    public ClaimResponse assessClaim(@PathVariable Long id,
            @Valid @RequestBody AssessmentRequest request) {
        return claimService.assessClaim(id, request);
    }

    @PostMapping("/{id}/tenant-response")
    public ClaimResponse tenantResponse(@PathVariable Long id,
            @Valid @RequestBody TenantResponseRequest request) {
        return claimService.tenantResponse(id, request);
    }

    @PostMapping("/{id}/support-decision")
    public ClaimResponse supportDecision(@PathVariable Long id,
            @Valid @RequestBody SupportDecisionRequest request) {
        return claimService.supportDecision(id, request);
    }

    @GetMapping("/{id}")
    public ClaimResponse getClaim(@PathVariable Long id) {
        return claimService.getClaim(id);
    }

    @GetMapping("/landlord/{landlordId}")
    public java.util.List<ClaimResponse> getClaimsForLandlord(@PathVariable Long landlordId,
            @RequestParam(name = "openOnly", defaultValue = "true") boolean openOnly) {
        return claimService.getClaimsForLandlord(landlordId, openOnly);
    }

    @GetMapping("/{id}/attachments/additional")
    public java.util.List<String> getAdditionalInfoAttachments(@PathVariable Long id) {
        return claimService.getAdditionalInfoAttachmentKeys(id);
    }
}
