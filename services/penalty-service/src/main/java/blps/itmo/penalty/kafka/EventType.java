package blps.itmo.penalty.kafka;

public final class EventType {

    public static final String PENALTY_APPLICATION_REQUESTED = "PENALTY_APPLICATION_REQUESTED";
    public static final String PENALTY_APPLIED = "PENALTY_APPLIED";
    public static final String PENALTY_APPLICATION_FAILED = "PENALTY_APPLICATION_FAILED";
    public static final String PENALTY_REVOKE_COMMAND = "PENALTY_REVOKE_COMMAND";
    public static final String PENALTY_REVOKED = "PENALTY_REVOKED";
    public static final String PENALTY_REVOKE_FAILED = "PENALTY_REVOKE_FAILED";

    private EventType() {
    }
}
