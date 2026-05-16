package blps.itmo.claim.saga;

public enum SagaState {
    STARTED,
    AWAITING_PENALTY_APPLIED,
    AWAITING_PENALTY_COUNTED,
    COMPLETED,
    PENALTY_FAILED,
    COMPENSATING_REVOKE,
    COMPENSATED,
    COMPENSATION_FAILED;

    public boolean isTerminal() {
        return this == COMPLETED
                || this == PENALTY_FAILED
                || this == COMPENSATED
                || this == COMPENSATION_FAILED;
    }
}
