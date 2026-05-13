package blps.itmo.claim.api.dto;

public record SupportDecisionRequest(
        boolean applyPenalty,
        String resolutionNote
) {
}
