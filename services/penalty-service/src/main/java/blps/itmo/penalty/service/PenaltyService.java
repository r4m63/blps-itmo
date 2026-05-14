package blps.itmo.penalty.service;

import blps.itmo.penalty.api.dto.CreatePenaltyRequest;
import blps.itmo.penalty.api.dto.FailPenaltyRequest;
import blps.itmo.penalty.domain.PenaltyOperation;
import blps.itmo.penalty.domain.PenaltyStatus;
import blps.itmo.penalty.messaging.EventType;
import blps.itmo.penalty.messaging.OutboxService;
import blps.itmo.penalty.messaging.TopicNames;
import blps.itmo.penalty.messaging.payload.PenaltyAppliedPayload;
import blps.itmo.penalty.messaging.payload.PenaltyApplicationFailedPayload;
import blps.itmo.penalty.messaging.payload.PenaltyApplicationRequestedPayload;
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
    private final OutboxService outboxService;

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
        op.setStatus(PenaltyStatus.PENDING);
        return repository.save(op);
    }

    @Transactional
    public PenaltyOperation handlePenaltyRequested(PenaltyApplicationRequestedPayload payload) {
        PenaltyOperation op = repository.findByClaimId(payload.claimId()).orElseGet(() -> {
            PenaltyOperation created = new PenaltyOperation();
            created.setClaimId(payload.claimId());
            created.setTenantId(payload.tenantId());
            created.setLandlordId(payload.landlordId());
            created.setRequestedBy(payload.requestedBy());
            created.setAmount(payload.amount());
            if (payload.currency() != null && !payload.currency().isBlank()) {
                created.setCurrency(payload.currency());
            }
            created.setStatus(PenaltyStatus.PENDING);
            return repository.save(created);
        });
        if (op.getStatus() == PenaltyStatus.PENDING) {
            return processPending(op, payload.simulateFailure());
        }
        return op;
    }

    @Transactional
    public PenaltyOperation apply(Integer id) {
        PenaltyOperation op = getById(id);
        ensureStatus(op, PenaltyStatus.PENDING);
        return applyInternal(op);
    }

    @Transactional
    public PenaltyOperation fail(Integer id, FailPenaltyRequest req) {
        PenaltyOperation op = getById(id);
        ensureStatus(op, PenaltyStatus.PENDING);
        return failInternal(op, req.reason());
    }

    @Transactional
    public PenaltyOperation retry(Integer id) {
        PenaltyOperation op = getById(id);
        if (op.getStatus() != PenaltyStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "only FAILED operations can be retried (current: %s)".formatted(op.getStatus()));
        }
        op.setStatus(PenaltyStatus.PENDING);
        op.setFailureReason(null);
        return repository.save(op);
    }

    @Transactional
    public int retryFailedBatch(int limit) {
        List<PenaltyOperation> failed = repository.findTop50ByStatus(PenaltyStatus.FAILED);
        int count = 0;
        for (PenaltyOperation op : failed) {
            if (count >= limit) {
                break;
            }
            op.setStatus(PenaltyStatus.PENDING);
            op.setFailureReason(null);
            repository.save(op);
            count++;
        }
        return count;
    }

    @Transactional
    public PenaltyOperation processPending(PenaltyOperation op, boolean simulateFailure) {
        ensureStatus(op, PenaltyStatus.PENDING);
        op.setStatus(PenaltyStatus.PROCESSING);
        repository.save(op);
        if (simulateFailure) {
            return failInternal(op, "simulated failure");
        }
        return applyInternal(op);
    }

    private PenaltyOperation applyInternal(PenaltyOperation op) {
        op.setStatus(PenaltyStatus.APPLIED);
        op.setAppliedAt(Instant.now());
        PenaltyOperation saved = repository.save(op);
        outboxService.enqueue(
                "penalty",
                saved.getId().toString(),
                EventType.PENALTY_APPLIED,
                TopicNames.PENALTY_EVENTS,
                new PenaltyAppliedPayload(saved.getClaimId(), saved.getId(), saved.getTenantId())
        );
        return saved;
    }

    private PenaltyOperation failInternal(PenaltyOperation op, String reason) {
        op.setStatus(PenaltyStatus.FAILED);
        op.setFailureReason(reason);
        PenaltyOperation saved = repository.save(op);
        outboxService.enqueue(
                "penalty",
                saved.getId().toString(),
                EventType.PENALTY_APPLICATION_FAILED,
                TopicNames.PENALTY_EVENTS,
                new PenaltyApplicationFailedPayload(saved.getClaimId(), saved.getId(), reason)
        );
        return saved;
    }

    private void ensureStatus(PenaltyOperation op, PenaltyStatus expected) {
        if (op.getStatus() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "operation is in %s, expected %s".formatted(op.getStatus(), expected));
        }
    }
}
