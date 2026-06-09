package blps.itmo.claim.api.dto;

public record IntakeDecisionRequest(
        boolean requestAdditionalInfo,
        String note
) {
}
