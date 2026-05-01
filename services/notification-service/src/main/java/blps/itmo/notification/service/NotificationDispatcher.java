package blps.itmo.notification.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import blps.itmo.platform.events.EventEnvelope;
import blps.itmo.platform.events.EventType;

@Component
public class NotificationDispatcher {

    private final ObjectMapper objectMapper;

    public NotificationDispatcher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record NotificationIntent(
            Long recipientUserId,
            String channel,
            String templateKey,
            String subject,
            String message) {
    }

    public List<NotificationIntent> dispatch(EventEnvelope envelope) {
        List<NotificationIntent> intents = new ArrayList<>();
        JsonNode payload = envelope.getPayload();
        EventType type = envelope.getEventType();

        switch (type) {
            case CLAIM_CREATED -> {
                Long tenantId = longField(payload, "tenantId");
                Long claimId = longField(payload, "claimId");
                if (tenantId != null) {
                    intents.add(new NotificationIntent(
                            tenantId, "IN_APP", "claim.created.tenant",
                            "New claim filed against you",
                            "Claim #" + claimId + " has been filed against you. Please review and respond."));
                }
            }
            case ASSESSMENT_COMPLETED -> {
                Long claimId = longField(payload, "claimId");
                Long landlordId = envelope.getActorId();
                if (landlordId != null && landlordId > 0) {
                    boolean penaltyGrounds = boolField(payload, "penaltyGrounds");
                    intents.add(new NotificationIntent(
                            landlordId, "IN_APP", "assessment.completed.landlord",
                            "Assessment complete for claim #" + claimId,
                            "Assessment for claim #" + claimId + " is complete. "
                                    + (penaltyGrounds ? "Penalty grounds found." : "No penalty grounds found.")));
                }
            }
            case ASSESSMENT_FAILED -> {
                Long claimId = longField(payload, "claimId");
                Long landlordId = envelope.getActorId();
                if (landlordId != null && landlordId > 0) {
                    intents.add(new NotificationIntent(
                            landlordId, "IN_APP", "assessment.failed.landlord",
                            "Assessment failed for claim #" + claimId,
                            "Assessment for claim #" + claimId + " failed and requires manual review."));
                }
            }
            case TENANT_RESPONSE_RECEIVED -> {
                Long claimId = longField(payload, "claimId");
                if (envelope.getActorId() != null) {
                    intents.add(new NotificationIntent(
                            envelope.getActorId(), "IN_APP", "tenant.response.received.tenant",
                            "Your response for claim #" + claimId + " was recorded",
                            "Your response has been submitted and claim #" + claimId + " moved to support review."));
                }
            }
            case TENANT_RESPONSE_EXPIRED -> {
                Long claimId = longField(payload, "claimId");
                Long tenantId = longField(payload, "tenantId");
                if (tenantId == null) tenantId = envelope.getActorId();
                if (tenantId != null) {
                    intents.add(new NotificationIntent(
                            tenantId, "IN_APP", "tenant.response.expired.tenant",
                            "Response deadline passed for claim #" + claimId,
                            "You did not respond to claim #" + claimId + " within the deadline. Claim moved to support review."));
                }
            }
            case CLAIM_CLOSED_NO_PENALTY -> {
                Long claimId = longField(payload, "claimId");
                intents.add(new NotificationIntent(
                        envelope.getActorId() != null ? envelope.getActorId() : 0L,
                        "IN_APP", "claim.closed.no_penalty",
                        "Claim #" + claimId + " closed without penalty",
                        "Claim #" + claimId + " has been resolved with no penalty applied."));
            }
            case PENALTY_APPLICATION_REQUESTED -> {
                Long tenantId = longField(payload, "tenantId");
                Long claimId = longField(payload, "claimId");
                if (tenantId != null) {
                    intents.add(new NotificationIntent(
                            tenantId, "IN_APP", "penalty.application.requested.tenant",
                            "Penalty processing started for claim #" + claimId,
                            "A penalty has been requested for claim #" + claimId + " and is being processed."));
                }
            }
            case PENALTY_APPLIED -> {
                Long tenantId = longField(payload, "tenantId");
                Long claimId = longField(payload, "claimId");
                String amount = textField(payload, "penaltyAmount");
                String currency = textField(payload, "penaltyCurrency");
                if (tenantId != null) {
                    intents.add(new NotificationIntent(
                            tenantId, "IN_APP", "penalty.applied.tenant",
                            "Penalty applied for claim #" + claimId,
                            "A penalty of " + amount + " " + currency + " has been applied to your account for claim #" + claimId + "."));
                }
                Long landlordId = envelope.getActorId();
                if (landlordId != null && landlordId > 0 && !landlordId.equals(tenantId)) {
                    intents.add(new NotificationIntent(
                            landlordId, "IN_APP", "penalty.applied.landlord",
                            "Penalty applied for claim #" + claimId,
                            "Penalty of " + amount + " " + currency + " has been applied for claim #" + claimId + "."));
                }
            }
            case PENALTY_APPLICATION_FAILED -> {
                Long claimId = longField(payload, "claimId");
                intents.add(new NotificationIntent(
                        envelope.getActorId() != null ? envelope.getActorId() : 0L,
                        "IN_APP", "penalty.failed",
                        "Penalty application failed for claim #" + claimId,
                        "Penalty processing for claim #" + claimId + " failed. Manual retry required."));
            }
            case USER_DEACTIVATED -> {
                Long userId = longField(payload, "userId");
                String reason = textField(payload, "reason");
                if (userId != null) {
                    intents.add(new NotificationIntent(
                            userId, "IN_APP", "user.deactivated",
                            "Your account has been deactivated",
                            "Your account has been deactivated. Reason: " + reason));
                }
            }
            case ATTACHMENT_BOUND -> {
                Long claimId = longField(payload, "claimId");
                if (envelope.getActorId() != null) {
                    intents.add(new NotificationIntent(
                            envelope.getActorId(), "IN_APP", "attachment.bound",
                            "Attachments bound to claim #" + claimId,
                            "Your attachments have been successfully bound to claim #" + claimId + "."));
                }
            }
            case ATTACHMENT_BINDING_FAILED -> {
                Long claimId = longField(payload, "claimId");
                Long landlordId = longField(payload, "landlordId");
                if (landlordId != null) {
                    intents.add(new NotificationIntent(
                            landlordId, "IN_APP", "attachment.binding.failed",
                            "Attachment binding failed for claim #" + claimId,
                            "Some attachments could not be bound to claim #" + claimId + ". Please check and retry."));
                }
            }
            default -> {
                // no notification for other events
            }
        }
        return intents;
    }

    private Long longField(JsonNode payload, String field) {
        if (payload == null || !payload.has(field) || payload.get(field).isNull()) return null;
        return payload.get(field).asLong();
    }

    private boolean boolField(JsonNode payload, String field) {
        if (payload == null || !payload.has(field)) return false;
        return payload.get(field).asBoolean();
    }

    private String textField(JsonNode payload, String field) {
        if (payload == null || !payload.has(field) || payload.get(field).isNull()) return "";
        return payload.get(field).asText();
    }
}
