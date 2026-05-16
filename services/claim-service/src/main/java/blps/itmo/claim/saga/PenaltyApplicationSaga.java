package blps.itmo.claim.saga;

import blps.itmo.claim.domain.Claim;
import blps.itmo.claim.domain.ClaimStatus;
import blps.itmo.claim.domain.ClaimStatusHistory;
import blps.itmo.claim.kafka.EventType;
import blps.itmo.claim.kafka.outboxevent.OutboxService;
import blps.itmo.claim.kafka.config.TopicNames;
import blps.itmo.claim.kafka.payload.PenaltyApplicationFailedPayload;
import blps.itmo.claim.kafka.payload.PenaltyAppliedPayload;
import blps.itmo.claim.kafka.payload.PenaltyCountedPayload;
import blps.itmo.claim.kafka.payload.PenaltyRevokeCommandPayload;
import blps.itmo.claim.kafka.payload.PenaltyRevokeFailedPayload;
import blps.itmo.claim.kafka.payload.PenaltyRevokedPayload;
import blps.itmo.claim.repository.ClaimRepository;
import blps.itmo.claim.repository.ClaimStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PenaltyApplicationSaga {

    private final SagaInstanceRepository sagaRepository;
    private final ClaimRepository claimRepository;
    private final ClaimStatusHistoryRepository historyRepository;
    private final OutboxService outboxService;

    // 1. СТАРТ саги
    @Transactional
    public SagaInstance start(Integer claimId) {
        SagaInstance saga = new SagaInstance();
        saga.setSagaId(UUID.randomUUID());
        saga.setSagaType(SagaType.PENALTY_APPLICATION);
        saga.setClaimId(claimId);
        saga.setState(SagaState.AWAITING_PENALTY_APPLIED);
        saga.setStartedAt(Instant.now());
        saga.setLastEventAt(Instant.now());
        return sagaRepository.save(saga);
    }

    // 2. Прогрессивные переходы (forward)
    @Transactional
    public void onPenaltyApplied(UUID sagaId, PenaltyAppliedPayload payload) {
        SagaInstance saga = sagaRepository.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("PENALTY_APPLIED for unknown saga {}; falling back to legacy claim transition", sagaId);
            return;
        }
        if (saga.getState() != SagaState.AWAITING_PENALTY_APPLIED) {
            log.info("PENALTY_APPLIED in unexpected state {} for saga {} — ignored", saga.getState(), sagaId);
            return;
        }
        saga.setState(SagaState.AWAITING_PENALTY_COUNTED);
        saga.setLastEventAt(Instant.now());
        sagaRepository.save(saga);
    }

    @Transactional
    public void onPenaltyApplicationFailed(UUID sagaId, PenaltyApplicationFailedPayload payload) {
        SagaInstance saga = sagaRepository.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("PENALTY_APPLICATION_FAILED for unknown saga {}", sagaId);
            return;
        }
        if (saga.getState() != SagaState.AWAITING_PENALTY_APPLIED) {
            log.info("PENALTY_APPLICATION_FAILED in unexpected state {} for saga {}", saga.getState(), sagaId);
            return;
        }
        saga.setState(SagaState.PENALTY_FAILED);
        saga.setFailureReason(payload.reason());
        Instant now = Instant.now();
        saga.setLastEventAt(now);
        saga.setCompletedAt(now);
        sagaRepository.save(saga);

        Claim claim = claimRepository.findById(saga.getClaimId()).orElse(null);
        if (claim != null && claim.getStatus() == ClaimStatus.PENALTY_PROCESSING) {
            claim.setStatus(ClaimStatus.PENALTY_PROCESSING_FAILED);
            claim.setClosedAt(null);
            claimRepository.save(claim);
            recordHistory(claim.getId(), ClaimStatus.PENALTY_PROCESSING, ClaimStatus.PENALTY_PROCESSING_FAILED,
                    null, payload.reason());
        }
    }

    @Transactional
    public void onPenaltyCounted(UUID sagaId, PenaltyCountedPayload payload) {
        SagaInstance saga = sagaRepository.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("PENALTY_COUNTED for unknown saga {}", sagaId);
            return;
        }
        if (saga.getState() != SagaState.AWAITING_PENALTY_COUNTED) {
            log.info("PENALTY_COUNTED in unexpected state {} for saga {}", saga.getState(), sagaId);
            return;
        }
        Instant now = Instant.now();
        saga.setState(SagaState.COMPLETED);
        saga.setLastEventAt(now);
        saga.setCompletedAt(now);
        sagaRepository.save(saga);

        Claim claim = claimRepository.findById(saga.getClaimId()).orElse(null);
        if (claim != null && claim.getStatus() == ClaimStatus.PENALTY_PROCESSING) {
            claim.setStatus(ClaimStatus.PENALTY_APPLIED);
            claim.setClosedAt(now);
            claimRepository.save(claim);
            recordHistory(claim.getId(), ClaimStatus.PENALTY_PROCESSING, ClaimStatus.PENALTY_APPLIED,
                    null, "saga completed: penalty applied & counted");
        }
    }

    // 3. Компенсация (rollback)
    @Transactional
    public void onPenaltyRevoked(UUID sagaId, PenaltyRevokedPayload payload) {
        SagaInstance saga = sagaRepository.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("PENALTY_REVOKED for unknown saga {}", sagaId);
            return;
        }
        if (saga.getState() != SagaState.COMPENSATING_REVOKE) {
            log.info("PENALTY_REVOKED in unexpected state {} for saga {}", saga.getState(), sagaId);
            return;
        }
        Instant now = Instant.now();
        saga.setState(SagaState.COMPENSATED);
        saga.setLastEventAt(now);
        saga.setCompletedAt(now);
        sagaRepository.save(saga);

        Claim claim = claimRepository.findById(saga.getClaimId()).orElse(null);
        if (claim != null && claim.getStatus() == ClaimStatus.PENALTY_PROCESSING) {
            ClaimStatus from = claim.getStatus();
            claim.setStatus(ClaimStatus.PENALTY_PROCESSING_FAILED);
            claim.setClosedAt(null);
            claimRepository.save(claim);
            recordHistory(claim.getId(), from, ClaimStatus.PENALTY_PROCESSING_FAILED,
                    null, "saga compensated: penalty revoked");
        }
    }

    @Transactional
    public void onPenaltyRevokeFailed(UUID sagaId, PenaltyRevokeFailedPayload payload) {
        SagaInstance saga = sagaRepository.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("PENALTY_REVOKE_FAILED for unknown saga {}", sagaId);
            return;
        }
        if (saga.getState() != SagaState.COMPENSATING_REVOKE) {
            return;
        }
        saga.setAttemptCount(saga.getAttemptCount() + 1);
        saga.setFailureReason(payload.reason());
        saga.setLastEventAt(Instant.now());
        if (saga.getAttemptCount() >= saga.getMaxAttempts()) {
            saga.setState(SagaState.COMPENSATION_FAILED);
            saga.setCompletedAt(Instant.now());
        }
        sagaRepository.save(saga);
    }

    @Transactional
    public void triggerCompensation(UUID sagaId, String reason) {
        SagaInstance saga = sagaRepository.findById(sagaId).orElse(null);
        if (saga == null) {
            return;
        }
        if (saga.getState() != SagaState.AWAITING_PENALTY_APPLIED
                && saga.getState() != SagaState.AWAITING_PENALTY_COUNTED
                && saga.getState() != SagaState.COMPENSATING_REVOKE) {
            return;
        }
        saga.setState(SagaState.COMPENSATING_REVOKE);
        saga.setLastEventAt(Instant.now());
        sagaRepository.save(saga);

        outboxService.enqueue(
                "saga",
                saga.getSagaId().toString(),
                EventType.PENALTY_REVOKE_COMMAND,
                TopicNames.PENALTY_EVENTS,
                new PenaltyRevokeCommandPayload(saga.getClaimId(), null, reason),
                saga.getSagaId()
        );
    }

    private void recordHistory(Integer claimId, ClaimStatus from, ClaimStatus to, Integer actorId, String note) {
        ClaimStatusHistory h = new ClaimStatusHistory();
        h.setClaimId(claimId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setActorId(actorId);
        h.setNote(note);
        historyRepository.save(h);
    }
}
