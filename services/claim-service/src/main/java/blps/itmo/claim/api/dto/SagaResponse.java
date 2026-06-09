package blps.itmo.claim.api.dto;

import blps.itmo.claim.saga.SagaState;
import blps.itmo.claim.saga.SagaType;
import blps.itmo.claim.saga.PenaltyApplicationSagaState;

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
    public static SagaResponse from(PenaltyApplicationSagaState s) {
        return new SagaResponse(
                s.sagaId(),
                s.sagaType(),
                s.claimId(),
                s.state(),
                s.attemptCount(),
                s.maxAttempts(),
                s.failureReason(),
                s.startedAt(),
                s.lastEventAt(),
                s.completedAt()
        );
    }
}
