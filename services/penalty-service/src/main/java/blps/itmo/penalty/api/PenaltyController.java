package blps.itmo.penalty.api;

import blps.itmo.penalty.api.dto.CreatePenaltyRequest;
import blps.itmo.penalty.api.dto.FailPenaltyRequest;
import blps.itmo.penalty.api.dto.PenaltyOperationResponse;
import blps.itmo.penalty.domain.PenaltyStatus;
import blps.itmo.penalty.service.PenaltyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/penalties")
@RequiredArgsConstructor
public class PenaltyController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final PenaltyService penaltyService;

    @PostMapping
    public ResponseEntity<PenaltyOperationResponse> create(@RequestHeader(USER_ID_HEADER) Integer requestedBy,
                                                           @Valid @RequestBody CreatePenaltyRequest req) {
        PenaltyOperationResponse body = PenaltyOperationResponse.from(penaltyService.create(requestedBy, req));
        return ResponseEntity.created(URI.create("/api/penalties/" + body.id())).body(body);
    }

    @GetMapping("/{id}")
    public PenaltyOperationResponse getById(@PathVariable Integer id) {
        return PenaltyOperationResponse.from(penaltyService.getById(id));
    }

    @GetMapping("/claim/{claimId}")
    public PenaltyOperationResponse getByClaim(@PathVariable Integer claimId) {
        return PenaltyOperationResponse.from(penaltyService.getByClaimId(claimId));
    }

    @GetMapping
    public List<PenaltyOperationResponse> list(@RequestParam(required = false) PenaltyStatus status,
                                               @RequestParam(required = false) Integer tenantId) {
        List<PenaltyOperationResponse> result;
        if (status != null) {
            result = penaltyService.listByStatus(status).stream().map(PenaltyOperationResponse::from).toList();
        } else if (tenantId != null) {
            result = penaltyService.listByTenant(tenantId).stream().map(PenaltyOperationResponse::from).toList();
        } else {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "status or tenantId query param required");
        }
        return result;
    }

    @PostMapping("/{id}/apply")
    public PenaltyOperationResponse apply(@PathVariable Integer id) {
        return PenaltyOperationResponse.from(penaltyService.apply(id));
    }

    @PostMapping("/{id}/fail")
    public PenaltyOperationResponse fail(@PathVariable Integer id,
                                         @Valid @RequestBody FailPenaltyRequest req) {
        return PenaltyOperationResponse.from(penaltyService.fail(id, req));
    }

    @PostMapping("/{id}/retry")
    public PenaltyOperationResponse retry(@PathVariable Integer id) {
        return PenaltyOperationResponse.from(penaltyService.retry(id));
    }
}
