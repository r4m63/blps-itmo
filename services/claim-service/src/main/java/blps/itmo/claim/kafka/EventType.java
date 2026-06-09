package blps.itmo.claim.kafka;

public final class EventType {

    public static final String CLAIM_CREATED = "CLAIM_CREATED";
    public static final String PENALTY_APPLICATION_REQUESTED = "PENALTY_APPLICATION_REQUESTED";
    public static final String PENALTY_APPLIED = "PENALTY_APPLIED";
    public static final String PENALTY_APPLICATION_FAILED = "PENALTY_APPLICATION_FAILED";
    public static final String PENALTY_COUNTED = "PENALTY_COUNTED";
    public static final String PENALTY_REVOKE_COMMAND = "PENALTY_REVOKE_COMMAND";
    public static final String PENALTY_REVOKED = "PENALTY_REVOKED";
    public static final String PENALTY_REVOKE_FAILED = "PENALTY_REVOKE_FAILED";
    public static final String TENANT_RESPONSE_EXPIRED = "TENANT_RESPONSE_EXPIRED";
    public static final String USER_DEACTIVATED = "USER_DEACTIVATED";

    private EventType() {
    }
}
