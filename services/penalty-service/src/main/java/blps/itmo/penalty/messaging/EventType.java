package blps.itmo.penalty.messaging;

public final class EventType {

    public static final String PENALTY_APPLICATION_REQUESTED = "PENALTY_APPLICATION_REQUESTED";
    public static final String PENALTY_APPLIED = "PENALTY_APPLIED";
    public static final String PENALTY_APPLICATION_FAILED = "PENALTY_APPLICATION_FAILED";

    private EventType() {
    }
}
