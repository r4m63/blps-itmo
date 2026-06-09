package blps.itmo.claim.api;

import blps.itmo.claim.api.dto.AdditionalInfoRequest;
import blps.itmo.claim.api.dto.AssessmentRequest;
import blps.itmo.claim.api.dto.ClaimMessageResponse;
import blps.itmo.claim.api.dto.ClaimResponse;
import blps.itmo.claim.api.dto.ClaimStatusHistoryResponse;
import blps.itmo.claim.api.dto.CreateClaimRequest;
import blps.itmo.claim.api.dto.CreateMessageRequest;
import blps.itmo.claim.api.dto.IntakeDecisionRequest;
import blps.itmo.claim.api.dto.SupportDecisionRequest;
import blps.itmo.claim.api.dto.TenantResponseRequest;
import blps.itmo.claim.domain.ClaimStatus;
import blps.itmo.claim.service.ClaimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
public class ClaimController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final ClaimService claimService;

    @PostMapping
    public ResponseEntity<ClaimResponse> create(@RequestHeader(USER_ID_HEADER) Integer landlordId,
                                                @Valid @RequestBody CreateClaimRequest req) {
        ClaimResponse body = ClaimResponse.from(claimService.create(landlordId, req));
        return ResponseEntity.created(URI.create("/api/claims/" + body.id())).body(body);
    }

    @GetMapping("/{id}")
    public ClaimResponse getById(@PathVariable Integer id) {
        return ClaimResponse.from(claimService.getById(id));
    }

    @GetMapping
    public List<ClaimResponse> list(@RequestHeader(USER_ID_HEADER) Integer userId,
                                    @RequestParam(required = false) ClaimStatus status) {
        return (status != null ? claimService.listByStatus(status) : claimService.listForUser(userId))
                .stream().map(ClaimResponse::from).toList();
    }

    @PostMapping("/{id}/intake-start")
    public ClaimResponse startIntake(@PathVariable Integer id,
                                     @RequestHeader(USER_ID_HEADER) Integer adminId) {
        return ClaimResponse.from(claimService.startIntake(id, adminId));
    }

    @PostMapping("/{id}/intake-decision")
    public ClaimResponse intakeDecision(@PathVariable Integer id,
                                        @RequestHeader(USER_ID_HEADER) Integer adminId,
                                        @Valid @RequestBody IntakeDecisionRequest req) {
        return ClaimResponse.from(claimService.intakeDecision(id, adminId, req));
    }

    @PostMapping("/{id}/additional-info")
    public ClaimResponse provideAdditionalInfo(@PathVariable Integer id,
                                               @RequestHeader(USER_ID_HEADER) Integer landlordId,
                                               @Valid @RequestBody AdditionalInfoRequest req) {
        return ClaimResponse.from(claimService.provideAdditionalInfo(id, landlordId, req));
    }

    @PostMapping("/{id}/assessment")
    public ClaimResponse assess(@PathVariable Integer id,
                                @RequestHeader(USER_ID_HEADER) Integer adminId,
                                @Valid @RequestBody AssessmentRequest req) {
        return ClaimResponse.from(claimService.assess(id, adminId, req));
    }

    @PostMapping("/{id}/tenant-response")
    public ClaimResponse tenantResponse(@PathVariable Integer id,
                                        @RequestHeader(USER_ID_HEADER) Integer tenantId,
                                        @Valid @RequestBody TenantResponseRequest req) {
        return ClaimResponse.from(claimService.tenantResponse(id, tenantId, req));
    }

    @PostMapping("/{id}/support-decision")
    public ClaimResponse supportDecision(@PathVariable Integer id,
                                         @RequestHeader(USER_ID_HEADER) Integer adminId,
                                         @Valid @RequestBody SupportDecisionRequest req) {
        return ClaimResponse.from(claimService.supportDecision(id, adminId, req));
    }

    @GetMapping("/{id}/messages")
    public List<ClaimMessageResponse> listMessages(@PathVariable Integer id) {
        return claimService.listMessages(id).stream().map(ClaimMessageResponse::from).toList();
    }

    @PostMapping("/{id}/messages")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public ClaimMessageResponse addMessage(@PathVariable Integer id,
                                           @RequestHeader(USER_ID_HEADER) Integer userId,
                                           @Valid @RequestBody CreateMessageRequest req) {
        return ClaimMessageResponse.from(claimService.addMessage(id, userId, req));
    }

    @GetMapping("/{id}/history")
    public List<ClaimStatusHistoryResponse> history(@PathVariable Integer id) {
        return claimService.listHistory(id).stream().map(ClaimStatusHistoryResponse::from).toList();
    }
}
