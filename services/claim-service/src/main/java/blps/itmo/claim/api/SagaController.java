package blps.itmo.claim.api;

import blps.itmo.claim.api.dto.SagaResponse;
import blps.itmo.claim.domain.Claim;
import blps.itmo.claim.repository.ClaimRepository;
import blps.itmo.claim.saga.PenaltyApplicationSaga;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SagaController {

    private final PenaltyApplicationSaga penaltyApplicationSaga;
    private final ClaimRepository claimRepository;

    @GetMapping("/sagas/{sagaId}")
    public SagaResponse getSaga(@PathVariable UUID sagaId) {
        return SagaResponse.from(penaltyApplicationSaga.describe(sagaId));
    }

    @GetMapping("/claims/{claimId}/saga")
    public SagaResponse getSagaForClaim(@PathVariable Integer claimId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found: " + claimId));
        if (claim.getCurrentSagaId() != null) {
            return SagaResponse.from(penaltyApplicationSaga.describe(claim.getCurrentSagaId()));
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No saga for claim: " + claimId);
    }
}
