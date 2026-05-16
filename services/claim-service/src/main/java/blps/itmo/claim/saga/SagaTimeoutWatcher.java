package blps.itmo.claim.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaTimeoutWatcher {

    private final SagaInstanceRepository sagaRepository;
    private final PenaltyApplicationSaga penaltyApplicationSaga;

    @Value("${saga.timeout-seconds.awaiting-penalty-applied:60}")
    private long awaitingPenaltyAppliedSeconds;

    @Value("${saga.timeout-seconds.awaiting-penalty-counted:60}")
    private long awaitingPenaltyCountedSeconds;

    @Value("${saga.timeout-seconds.compensating-retry:30}")
    private long compensatingRetrySeconds;

    @Scheduled(fixedDelayString = "${saga.timeout-watcher-delay-ms:5000}")
    @Transactional
    public void sweep() {
        Instant now = Instant.now();
        sweepState(SagaState.AWAITING_PENALTY_APPLIED,
                now.minus(Duration.ofSeconds(awaitingPenaltyAppliedSeconds)),
                "timeout waiting for PENALTY_APPLIED");
        sweepState(SagaState.AWAITING_PENALTY_COUNTED,
                now.minus(Duration.ofSeconds(awaitingPenaltyCountedSeconds)),
                "timeout waiting for PENALTY_COUNTED");
        sweepState(SagaState.COMPENSATING_REVOKE,
                now.minus(Duration.ofSeconds(compensatingRetrySeconds)),
                "retry: revoke command timeout");
    }

    private void sweepState(SagaState state, Instant threshold, String reason) {
        List<SagaInstance> stuck = sagaRepository.findStuck(List.of(state), threshold);
        for (SagaInstance saga : stuck) {
            log.warn("Saga {} stuck in {} since {} — driving compensation ({})",
                    saga.getSagaId(), state, saga.getLastEventAt(), reason);
            penaltyApplicationSaga.triggerCompensation(saga.getSagaId(), reason);
        }
    }
}
