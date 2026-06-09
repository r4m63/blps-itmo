package blps.itmo.claim.saga;

import java.time.Instant;
import java.util.UUID;

public record PenaltyApplicationSagaState(
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
}
