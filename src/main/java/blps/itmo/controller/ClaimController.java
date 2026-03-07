package blps.itmo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import blps.itmo.dto.AttachmentRequest;
import blps.itmo.dto.ClaimDto;
import blps.itmo.dto.CreateClaimRequest;
import blps.itmo.dto.IntakeCheckRequest;
import blps.itmo.dto.ProvideDocsRequest;
import blps.itmo.dto.RespondentResponseRequest;
import blps.itmo.dto.RulesCheckRequest;
import blps.itmo.dto.SupportDecisionRequest;
import blps.itmo.entity.Claim;
import blps.itmo.service.ClaimService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimDto create(@Valid @RequestBody CreateClaimRequest request) {
        Claim claim = claimService.createClaim(
                request.initiatorId(),
                request.respondentId(),
                request.amount(),
                request.currency(),
                request.reason());

        if (request.attachments() != null) {
            for (AttachmentRequest a : request.attachments()) {
                claimService.addAttachment(claim.getId(), a.uploaderId(), a.type(), a.url());
            }
        }
        return ClaimDto.from(claimService.get(claim.getId()));
    }

    @GetMapping("/{id}")
    public ClaimDto get(@PathVariable Long id) {
        return ClaimDto.from(claimService.get(id));
    }

    @PostMapping("/{id}/attachments")
    public ClaimDto addAttachment(@PathVariable Long id, @Valid @RequestBody AttachmentRequest request) {
        claimService.addAttachment(id, request.uploaderId(), request.type(), request.url());
        return ClaimDto.from(claimService.get(id));
    }

    @PostMapping("/{id}/intake-check")
    public ClaimDto intakeCheck(@PathVariable Long id, @Valid @RequestBody IntakeCheckRequest request) {
        claimService.intakeCheck(id, request.enoughData());
        return ClaimDto.from(claimService.get(id));
    }

    @PostMapping("/{id}/provide-docs")
    public ClaimDto provideDocs(@PathVariable Long id, @Valid @RequestBody ProvideDocsRequest request) {
        for (AttachmentRequest a : request.attachments()) {
            claimService.addAttachment(id, a.uploaderId(), a.type(), a.url());
        }
        claimService.provideAdditionalData(id);
        return ClaimDto.from(claimService.get(id));
    }

    @PostMapping("/{id}/rules-check")
    public ClaimDto rulesCheck(@PathVariable Long id, @Valid @RequestBody RulesCheckRequest request) {
        claimService.rulesCheck(id, request.groundsForPenalty());
        return ClaimDto.from(claimService.get(id));
    }

    @PostMapping("/{id}/respondent/response")
    public ClaimDto respondentResponse(@PathVariable Long id, @Valid @RequestBody RespondentResponseRequest request) {
        claimService.respondentResponse(id, request.comment());
        return ClaimDto.from(claimService.get(id));
    }

    @PostMapping("/{id}/respondent/timeout")
    public ClaimDto timeout(@PathVariable Long id) {
        claimService.markTimeout(id);
        return ClaimDto.from(claimService.get(id));
    }

    @PostMapping("/{id}/support-decision")
    public ClaimDto supportDecision(@PathVariable Long id, @Valid @RequestBody SupportDecisionRequest request) {
        claimService.supportDecision(id, request.approvePenalty(), request.comment());
        return ClaimDto.from(claimService.get(id));
    }
}
