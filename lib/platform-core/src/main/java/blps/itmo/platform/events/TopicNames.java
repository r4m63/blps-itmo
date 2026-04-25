package blps.itmo.platform.events;

public final class TopicNames {

    public static final String CLAIM_EVENTS = "claim.events";
    public static final String ASSESSMENT_EVENTS = "assessment.events";
    public static final String PENALTY_EVENTS = "penalty.events";
    public static final String AUTH_EVENTS = "auth.events";

    private TopicNames() {
    }

    public static String resolve(EventType type) {
        return switch (type) {
            case CLAIM_CREATED, ADDITIONAL_INFO_PROVIDED, TENANT_RESPONSE_RECEIVED, CLAIM_CLOSED_NO_PENALTY
                    -> CLAIM_EVENTS;
            case ASSESSMENT_COMPLETED -> ASSESSMENT_EVENTS;
            case PENALTY_APPLICATION_REQUESTED, PENALTY_APPLIED, PENALTY_APPLICATION_FAILED -> PENALTY_EVENTS;
            case USER_DEACTIVATED -> AUTH_EVENTS;
        };
    }
}
