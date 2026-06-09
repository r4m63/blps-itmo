package blps.itmo.claim.process;

import blps.itmo.claim.kafka.payload.PenaltyApplicationFailedPayload;
import blps.itmo.claim.kafka.payload.PenaltyCountedPayload;
import blps.itmo.claim.kafka.payload.PenaltyRevokedPayload;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("claimProcessDelegates")
@RequiredArgsConstructor
public class ClaimProcessDelegates {

    private final ClaimProcessActions actions;

    public void recordIntakeStarted(DelegateExecution execution) {
        actions.recordIntakeStarted(
                ProcessVariables.integer(execution, "claimId"),
                ProcessVariables.integer(execution, "adminId")
        );
    }

    public void recordIntakeDecision(DelegateExecution execution) {
        actions.recordIntakeDecision(
                ProcessVariables.integer(execution, "claimId"),
                ProcessVariables.integer(execution, "adminId"),
                ProcessVariables.bool(execution, "requestAdditionalInfo"),
                ProcessVariables.string(execution, "note")
        );
    }

    public void recordAdditionalInfo(DelegateExecution execution) {
        actions.recordAdditionalInfo(
                ProcessVariables.integer(execution, "claimId"),
                ProcessVariables.integer(execution, "landlordId"),
                ProcessVariables.string(execution, "additionalInfoBody")
        );
    }

    public void recordAssessment(DelegateExecution execution) {
        actions.recordAssessment(
                ProcessVariables.integer(execution, "claimId"),
                ProcessVariables.integer(execution, "adminId"),
                ProcessVariables.bool(execution, "penaltyGrounds"),
                ProcessVariables.decimal(execution, "assessmentAmount"),
                ProcessVariables.string(execution, "assessmentNotes")
        );
    }

    public void expireTenantResponse(DelegateExecution execution) {
        actions.expireTenantResponse(ProcessVariables.integer(execution, "claimId"));
    }

    public void recordTenantResponse(DelegateExecution execution) {
        actions.recordTenantResponse(
                ProcessVariables.integer(execution, "claimId"),
                ProcessVariables.integer(execution, "tenantId"),
                ProcessVariables.string(execution, "tenantResponseBody")
        );
    }

    public void recordSupportDecision(DelegateExecution execution) {
        actions.recordSupportDecision(
                ProcessVariables.integer(execution, "claimId"),
                ProcessVariables.integer(execution, "adminId"),
                ProcessVariables.bool(execution, "applyPenalty"),
                ProcessVariables.string(execution, "resolutionNote")
        );
    }

    public void startPenaltySaga(DelegateExecution execution) {
        UUID sagaId = actions.startPenaltySaga(ProcessVariables.integer(execution, "claimId"));
        execution.setVariable("sagaId", sagaId.toString());
        execution.setVariable("sagaAttemptCount", 0);
        ProcessVariables.state(execution, "STARTED");
    }

    public void requestPenaltyApplication(DelegateExecution execution) {
        actions.enqueuePenaltyApplication(
                ProcessVariables.integer(execution, "claimId"),
                ProcessVariables.integer(execution, "adminId"),
                ProcessVariables.decimal(execution, "penaltyAmount"),
                ProcessVariables.string(execution, "penaltyCurrency"),
                ProcessVariables.bool(execution, "simulateFailure"),
                ProcessVariables.uuid(execution, "sagaId")
        );
        ProcessVariables.state(execution, "AWAITING_PENALTY_APPLIED");
    }

    public void awaitPenaltyCounted(DelegateExecution execution) {
        ProcessVariables.state(execution, "AWAITING_PENALTY_COUNTED");
    }

    public void markPenaltyApplicationFailed(DelegateExecution execution) {
        String reason = ProcessVariables.string(execution, "penaltyFailureReason");
        actions.markPenaltyApplicationFailed(new PenaltyApplicationFailedPayload(
                ProcessVariables.integer(execution, "claimId"),
                ProcessVariables.integer(execution, "operationId"),
                reason
        ));
        execution.setVariable("sagaFailureReason", reason);
        ProcessVariables.terminalState(execution, "PENALTY_FAILED");
    }

    public void markPenaltyCounted(DelegateExecution execution) {
        actions.markPenaltyCounted(new PenaltyCountedPayload(
                ProcessVariables.integer(execution, "claimId"),
                ProcessVariables.integer(execution, "tenantId"),
                ProcessVariables.integer(execution, "operationId"),
                ProcessVariables.integer(execution, "newPenaltyCount")
        ));
        ProcessVariables.terminalState(execution, "COMPLETED");
    }

    public void startCompensation(DelegateExecution execution) {
        String reason = ProcessVariables.string(execution, "sagaFailureReason");
        if (reason == null || reason.isBlank()) {
            reason = "timeout waiting for downstream event";
            execution.setVariable("sagaFailureReason", reason);
        }
        ProcessVariables.state(execution, "COMPENSATING_REVOKE");
    }

    public void enqueuePenaltyRevoke(DelegateExecution execution) {
        Integer attempt = ProcessVariables.integer(execution, "sagaAttemptCount");
        if (attempt == null) {
            attempt = 0;
        }
        attempt++;
        execution.setVariable("sagaAttemptCount", attempt);
        actions.enqueuePenaltyRevoke(
                ProcessVariables.integer(execution, "claimId"),
                ProcessVariables.uuid(execution, "sagaId"),
                ProcessVariables.string(execution, "sagaFailureReason")
        );
    }

    public void notePenaltyRevokeFailed(DelegateExecution execution) {
        String reason = ProcessVariables.string(execution, "penaltyRevokeFailureReason");
        if (reason != null && !reason.isBlank()) {
            execution.setVariable("sagaFailureReason", reason);
        }
    }

    public void markPenaltyRevoked(DelegateExecution execution) {
        actions.markPenaltyRevoked(new PenaltyRevokedPayload(
                ProcessVariables.integer(execution, "claimId"),
                ProcessVariables.integer(execution, "operationId"),
                ProcessVariables.integer(execution, "tenantId"),
                true
        ));
        ProcessVariables.terminalState(execution, "COMPENSATED");
    }

    public void markCompensationFailed(DelegateExecution execution) {
        ProcessVariables.terminalState(execution, "COMPENSATION_FAILED");
    }
}
