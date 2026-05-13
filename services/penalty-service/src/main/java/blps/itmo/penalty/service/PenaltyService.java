package blps.itmo.penalty.service;

import blps.itmo.penalty.api.dto.CreatePenaltyRequest;
import blps.itmo.penalty.api.dto.FailPenaltyRequest;
import blps.itmo.penalty.domain.PenaltyOperation;
import blps.itmo.penalty.domain.PenaltyStatus;
import blps.itmo.penalty.repository.PenaltyOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PenaltyService {

    private final PenaltyOperationRepository repository;

    public PenaltyOperation getById(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Penalty operation not found: " + id));
    }

    public PenaltyOperation getByClaimId(Integer claimId) {
        return repository.findByClaimId(claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Penalty operation not found for claim: " + claimId));
    }

    public List<PenaltyOperation> listByStatus(PenaltyStatus status) {
        return repository.findByStatus(status);
    }

    public List<PenaltyOperation> listByTenant(Integer tenantId) {
        return repository.findByTenantId(tenantId);
    }

    @Transactional
    public PenaltyOperation create(Integer requestedBy, CreatePenaltyRequest req) {
        if (req.tenantId().equals(req.landlordId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant and landlord must differ");
        }
        repository.findByClaimId(req.claimId()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "penalty operation already exists for claim " + req.claimId());
        });

        PenaltyOperation op = new PenaltyOperation();
        op.setClaimId(req.claimId());
        op.setTenantId(req.tenantId());
        op.setLandlordId(req.landlordId());
        op.setRequestedBy(requestedBy);
        op.setAmount(req.amount());
        if (req.currency() != null && !req.currency().isBlank()) {
            op.setCurrency(req.currency());
        }
        op.setStatus(PenaltyStatus.REQUESTED);
        return repository.save(op);
    }

    @Transactional
    public PenaltyOperation apply(Integer id) {
        PenaltyOperation op = getById(id);
        ensureStatus(op, PenaltyStatus.REQUESTED);
        op.setStatus(PenaltyStatus.APPLIED);
        op.setAppliedAt(Instant.now());
        return repository.save(op);
    }

    @Transactional
    public PenaltyOperation fail(Integer id, FailPenaltyRequest req) {
        PenaltyOperation op = getById(id);
        ensureStatus(op, PenaltyStatus.REQUESTED);
        op.setStatus(PenaltyStatus.FAILED);
        op.setFailureReason(req.reason());
        return repository.save(op);
    }

    @Transactional
    public PenaltyOperation retry(Integer id) {
        PenaltyOperation op = getById(id);
        if (op.getStatus() != PenaltyStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "only FAILED operations can be retried (current: %s)".formatted(op.getStatus()));
        }
        op.setStatus(PenaltyStatus.REQUESTED);
        op.setFailureReason(null);
        return repository.save(op);
    }

    private void ensureStatus(PenaltyOperation op, PenaltyStatus expected) {
        if (op.getStatus() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "operation is in %s, expected %s".formatted(op.getStatus(), expected));
        }
    }
}
