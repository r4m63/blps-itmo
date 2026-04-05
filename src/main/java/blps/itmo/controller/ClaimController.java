package blps.itmo.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import blps.itmo.security.AppUserPrincipal;
import blps.itmo.service.ClaimService;
import jakarta.validation.Valid;

/**
 * REST API сервиса заявок на штрафные санкции.
 *
 * <h3>Матрица доступа</h3>
 * <pre>
 * | Операция                              | Привилегия                      | Роль     |
 * | ------------------------------------- | ------------------------------- | -------- |
 * | POST /api/claims                      | CLAIM_CREATE                    | LANDLORD |
 * | POST /api/claims/{id}/intake          | CLAIM_INTAKE_DECISION           | ADMIN    |
 * | POST /api/claims/{id}/additional-info | CLAIM_PROVIDE_ADDITIONAL_INFO   | LANDLORD |
 * | POST /api/claims/{id}/assessment      | CLAIM_ASSESS                    | ADMIN    |
 * | POST /api/claims/{id}/tenant-response | CLAIM_TENANT_RESPOND            | TENANT   |
 * | POST /api/claims/{id}/support-decision| CLAIM_SUPPORT_DECISION          | ADMIN    |
 * | GET  /api/claims/{id}                 | CLAIM_READ_OWN|CLAIM_READ_ANY   | любая    |
 * | GET  /api/claims/landlord/{id}        | CLAIM_READ_OWN|CLAIM_READ_ANY   | любая    |
 * </pre>
 *
 * Дополнительно на сервисном слое проверяется владение заявкой
 * (арендодатель/арендатор могут обращаться только к своим заявкам).
 */
@RestController
@RequestMapping("/api/claims")
@Validated
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(blps.itmo.security.Privileges).CLAIM_CREATE)")
    public ClaimResponse createClaim(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody CreateClaimRequest request) {
        return claimService.createClaim(principal.getUserId(), request);
    }

    @PostMapping("/{id}/intake")
    @PreAuthorize("hasAuthority(T(blps.itmo.security.Privileges).CLAIM_INTAKE_DECISION)")
    public ClaimResponse intakeDecision(@AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody IntakeDecisionRequest request) {
        return claimService.intakeDecision(id, principal.getUserId(), request);
    }

    @PostMapping("/{id}/additional-info")
    @PreAuthorize("hasAuthority(T(blps.itmo.security.Privileges).CLAIM_PROVIDE_ADDITIONAL_INFO)")
    public ClaimResponse submitAdditionalInfo(@AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AdditionalInfoReplyRequest request) {
        return claimService.additionalInfoReply(id, principal.getUserId(), request);
    }

    @PostMapping("/{id}/assessment")
    @PreAuthorize("hasAuthority(T(blps.itmo.security.Privileges).CLAIM_ASSESS)")
    public ClaimResponse assessClaim(@AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AssessmentRequest request) {
        return claimService.assessClaim(id, principal.getUserId(), request);
    }

    @PostMapping("/{id}/tenant-response")
    @PreAuthorize("hasAuthority(T(blps.itmo.security.Privileges).CLAIM_TENANT_RESPOND)")
    public ClaimResponse tenantResponse(@AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody TenantResponseRequest request) {
        return claimService.tenantResponse(id, principal.getUserId(), request);
    }

    @PostMapping("/{id}/support-decision")
    @PreAuthorize("hasAuthority(T(blps.itmo.security.Privileges).CLAIM_SUPPORT_DECISION)")
    public ClaimResponse supportDecision(@AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody SupportDecisionRequest request) {
        return claimService.supportDecision(id, principal.getUserId(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority(T(blps.itmo.security.Privileges).CLAIM_READ_OWN, "
            + "T(blps.itmo.security.Privileges).CLAIM_READ_ANY)")
    public ClaimResponse getClaim(@AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id) {
        return claimService.getClaim(id, principal.getUserId(), principal.getRole());
    }

    @GetMapping("/landlord/{landlordId}")
    @PreAuthorize("hasAnyAuthority(T(blps.itmo.security.Privileges).CLAIM_READ_OWN, "
            + "T(blps.itmo.security.Privileges).CLAIM_READ_ANY)")
    public List<ClaimResponse> getClaimsForLandlord(@AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long landlordId,
            @RequestParam(name = "openOnly", defaultValue = "true") boolean openOnly) {
        return claimService.getClaimsForLandlord(landlordId, openOnly, principal.getUserId(), principal.getRole());
    }

    @GetMapping("/{id}/attachments/additional")
    @PreAuthorize("hasAnyAuthority(T(blps.itmo.security.Privileges).CLAIM_READ_OWN, "
            + "T(blps.itmo.security.Privileges).CLAIM_READ_ANY)")
    public List<String> getAdditionalInfoAttachments(@AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id) {
        return claimService.getAdditionalInfoAttachmentKeys(id, principal.getUserId(), principal.getRole());
    }
}
