package blps.itmo.controller;

import blps.itmo.dto.AdditionalInfoReplyRequest;
import blps.itmo.dto.AssessmentRequest;
import blps.itmo.dto.ClaimResponse;
import blps.itmo.dto.CreateClaimRequest;
import blps.itmo.dto.IntakeDecisionRequest;
import blps.itmo.dto.TenantResponseRequest;
import blps.itmo.dto.SupportDecisionRequest;
import blps.itmo.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/claims")
@Validated
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    public ResponseEntity<ClaimResponse> createClaim(@Valid @RequestBody CreateClaimRequest request) {
        ClaimResponse response = claimService.createClaim(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/intake")
    public ResponseEntity<ClaimResponse> intakeDecision(@PathVariable Long id,
                                                        @Valid @RequestBody IntakeDecisionRequest request) {
        ClaimResponse response = claimService.intakeDecision(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/additional-info")
    public ResponseEntity<ClaimResponse> submitAdditionalInfo(@PathVariable Long id,
                                                              @Valid @RequestBody AdditionalInfoReplyRequest request) {
        ClaimResponse response = claimService.additionalInfoReply(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/assessment")
    public ResponseEntity<ClaimResponse> assessClaim(@PathVariable Long id,
                                                     @Valid @RequestBody AssessmentRequest request) {
        ClaimResponse response = claimService.assessClaim(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/tenant-response")
    public ResponseEntity<ClaimResponse> tenantResponse(@PathVariable Long id,
                                                        @Valid @RequestBody TenantResponseRequest request) {
        ClaimResponse response = claimService.tenantResponse(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/support-decision")
    public ResponseEntity<ClaimResponse> supportDecision(@PathVariable Long id,
                                                         @Valid @RequestBody SupportDecisionRequest request) {
        ClaimResponse response = claimService.supportDecision(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClaimResponse> getClaim(@PathVariable Long id) {
        ClaimResponse response = claimService.getClaim(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/landlord/{landlordId}")
    public ResponseEntity<java.util.List<ClaimResponse>> getClaimsForLandlord(@PathVariable Long landlordId,
                                                                              @RequestParam(name = "openOnly", defaultValue = "true") boolean openOnly) {
        return ResponseEntity.ok(claimService.getClaimsForLandlord(landlordId, openOnly));
    }

    @GetMapping("/{id}/attachments/additional")
    public ResponseEntity<java.util.List<String>> getAdditionalInfoAttachments(@PathVariable Long id) {
        return ResponseEntity.ok(claimService.getAdditionalInfoAttachmentKeys(id));
    }
}
