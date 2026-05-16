package blps.itmo.claim.api.dto;

import blps.itmo.claim.saga.SagaInstance;
import blps.itmo.claim.saga.SagaState;
import blps.itmo.claim.saga.SagaType;

import java.time.Instant;
import java.util.UUID;

public record SagaResponse(
        UUID sagaId,
        SagaType sagaType,
        Integer claimId,
        SagaState state,
        int attemptCount,
        int maxAttempts,
        String failureReason,
        Instant startedAt,
        Instant lastEventAt,
        Instant completedAt
) {
    public static SagaResponse from(SagaInstance s) {
        return new SagaResponse(
                s.getSagaId(),
                s.getSagaType(),
                s.getClaimId(),
                s.getState(),
                s.getAttemptCount(),
                s.getMaxAttempts(),
                s.getFailureReason(),
                s.getStartedAt(),
                s.getLastEventAt(),
                s.getCompletedAt()
        );
    }
}
