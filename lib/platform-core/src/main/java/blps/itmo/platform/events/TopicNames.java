package blps.itmo.platform.events;

public final class TopicNames {

    public static final String CLAIM_EVENTS = "claim.events";
    public static final String ASSESSMENT_EVENTS = "assessment.events";
    public static final String PENALTY_EVENTS = "penalty.events";
    public static final String AUTH_EVENTS = "auth.events";
    public static final String STORAGE_EVENTS = "storage.events";

    private TopicNames() {
    }

    public static String resolve(EventType type) {
        return switch (type) {
            case CLAIM_CREATED, ADDITIONAL_INFO_PROVIDED, TENANT_RESPONSE_RECEIVED, TENANT_RESPONSE_EXPIRED,
                    CLAIM_CLOSED_NO_PENALTY, ATTACHMENT_BINDING_REQUESTED, CLAIM_SUPPORT_REVIEW_EXPIRED
                    -> CLAIM_EVENTS;
            case ASSESSMENT_COMPLETED, ASSESSMENT_FAILED -> ASSESSMENT_EVENTS;
            case PENALTY_APPLICATION_REQUESTED, PENALTY_APPLIED, PENALTY_APPLICATION_FAILED -> PENALTY_EVENTS;
            case USER_DEACTIVATED -> AUTH_EVENTS;
            case ATTACHMENT_INITIALIZED, ATTACHMENT_CONFIRMED, ATTACHMENT_BOUND, ATTACHMENT_BINDING_FAILED
                    -> STORAGE_EVENTS;
        };
    }

    public static String dlt(String topicName) {
        return topicName + ".dlt";
    }
}
