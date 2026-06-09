package blps.itmo.penalty.service;

import blps.itmo.penalty.api.dto.CreatePenaltyRequest;
import blps.itmo.penalty.api.dto.FailPenaltyRequest;
import blps.itmo.penalty.domain.PenaltyOperation;
import blps.itmo.penalty.domain.PenaltyStatus;
import blps.itmo.penalty.kafka.EventType;
import blps.itmo.penalty.kafka.outboxevent.OutboxService;
import blps.itmo.penalty.kafka.config.TopicNames;
import blps.itmo.penalty.kafka.payload.PenaltyAppliedPayload;
import blps.itmo.penalty.kafka.payload.PenaltyApplicationFailedPayload;
import blps.itmo.penalty.kafka.payload.PenaltyApplicationRequestedPayload;
import blps.itmo.penalty.kafka.payload.PenaltyRevokeCommandPayload;
import blps.itmo.penalty.kafka.payload.PenaltyRevokeFailedPayload;
import blps.itmo.penalty.kafka.payload.PenaltyRevokedPayload;
import blps.itmo.penalty.repository.PenaltyOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
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
    public PenaltyOperation handlePenaltyRequested(PenaltyApplicationRequestedPayload payload, UUID sagaId) {
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
        if (sagaId != null) {
            op.setLastSagaId(sagaId);
            op = repository.save(op);
        }
        if (op.getStatus() == PenaltyStatus.PENDING) {
            return processPending(op, payload.simulateFailure(), sagaId);
        }
        return op;
    }

    @Transactional
    public PenaltyOperation apply(Integer id) {
        PenaltyOperation op = getById(id);
        ensureStatus(op, PenaltyStatus.PENDING);
        return applyInternal(op, op.getLastSagaId());
    }

    @Transactional
    public PenaltyOperation fail(Integer id, FailPenaltyRequest req) {
        PenaltyOperation op = getById(id);
        ensureStatus(op, PenaltyStatus.PENDING);
        return failInternal(op, req.reason(), op.getLastSagaId());
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
    public PenaltyOperation revoke(PenaltyRevokeCommandPayload payload, UUID sagaId) {
        PenaltyOperation op = repository.findByClaimId(payload.claimId()).orElse(null);

        // Idempotent: already revoked with this saga — re-emit success
        if (op != null && op.getStatus() == PenaltyStatus.REVOKED
                && sagaId != null && sagaId.equals(op.getLastSagaId())) {
            outboxService.enqueue(
                    "penalty",
                    op.getId().toString(),
                    EventType.PENALTY_REVOKED,
                    TopicNames.PENALTY_EVENTS,
                    new PenaltyRevokedPayload(op.getClaimId(), op.getId(), op.getTenantId(), false),
                    sagaId
            );
            return op;
        }

        // Vacuous success: nothing to revoke
        if (op == null) {
            outboxService.enqueue(
                    "penalty",
                    payload.claimId().toString(),
                    EventType.PENALTY_REVOKED,
                    TopicNames.PENALTY_EVENTS,
                    new PenaltyRevokedPayload(payload.claimId(), null, null, false),
                    sagaId
            );
            return null;
        }

        try {
            boolean wasApplied = op.getStatus() == PenaltyStatus.APPLIED;
            op.setStatus(PenaltyStatus.REVOKED);
            op.setRevokedAt(Instant.now());
            op.setRevokeReason(payload.reason());
            if (sagaId != null) {
                op.setLastSagaId(sagaId);
            }
            PenaltyOperation saved = repository.save(op);
            outboxService.enqueue(
                    "penalty",
                    saved.getId().toString(),
                    EventType.PENALTY_REVOKED,
                    TopicNames.PENALTY_EVENTS,
                    new PenaltyRevokedPayload(saved.getClaimId(), saved.getId(), saved.getTenantId(), wasApplied),
                    sagaId
            );
            return saved;
        } catch (RuntimeException ex) {
            outboxService.enqueue(
                    "penalty",
                    op.getId().toString(),
                    EventType.PENALTY_REVOKE_FAILED,
                    TopicNames.PENALTY_EVENTS,
                    new PenaltyRevokeFailedPayload(op.getClaimId(), op.getId(), ex.getMessage()),
                    sagaId
            );
            throw ex;
        }
    }

    @Transactional
    public PenaltyOperation processPending(PenaltyOperation op, boolean simulateFailure, UUID sagaId) {
        ensureStatus(op, PenaltyStatus.PENDING);
        op.setStatus(PenaltyStatus.PROCESSING);
        repository.save(op);
        if (simulateFailure) {
            return failInternal(op, "simulated failure", sagaId);
        }
        return applyInternal(op, sagaId);
    }

    private PenaltyOperation applyInternal(PenaltyOperation op, UUID sagaId) {
        op.setStatus(PenaltyStatus.APPLIED);
        op.setAppliedAt(Instant.now());
        if (sagaId != null) {
            op.setLastSagaId(sagaId);
        }
        PenaltyOperation saved = repository.save(op);
        outboxService.enqueue(
                "penalty",
                saved.getId().toString(),
                EventType.PENALTY_APPLIED,
                TopicNames.PENALTY_EVENTS,
                new PenaltyAppliedPayload(saved.getClaimId(), saved.getId(), saved.getTenantId()),
                sagaId
        );
        return saved;
    }

    private PenaltyOperation failInternal(PenaltyOperation op, String reason, UUID sagaId) {
        op.setStatus(PenaltyStatus.FAILED);
        op.setFailureReason(reason);
        if (sagaId != null) {
            op.setLastSagaId(sagaId);
        }
        PenaltyOperation saved = repository.save(op);
        outboxService.enqueue(
                "penalty",
                saved.getId().toString(),
                EventType.PENALTY_APPLICATION_FAILED,
                TopicNames.PENALTY_EVENTS,
                new PenaltyApplicationFailedPayload(saved.getClaimId(), saved.getId(), reason),
                sagaId
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
