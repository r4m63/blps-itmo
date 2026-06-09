package blps.itmo.claim.saga;

import blps.itmo.claim.kafka.payload.PenaltyApplicationFailedPayload;
import blps.itmo.claim.kafka.payload.PenaltyAppliedPayload;
import blps.itmo.claim.kafka.payload.PenaltyCountedPayload;
import blps.itmo.claim.kafka.payload.PenaltyRevokeFailedPayload;
import blps.itmo.claim.kafka.payload.PenaltyRevokedPayload;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.VariableInstance;
import org.camunda.bpm.engine.runtime.MessageCorrelationBuilder;
import org.camunda.bpm.engine.MismatchingMessageCorrelationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PenaltyApplicationSaga {

    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    public void onPenaltyApplied(UUID sagaId, PenaltyAppliedPayload payload) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("operationId", payload.operationId());
        variables.put("tenantId", payload.tenantId());
        correlate(sagaId, "PENALTY_APPLIED", variables);
    }

    public void onPenaltyApplicationFailed(UUID sagaId, PenaltyApplicationFailedPayload payload) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("operationId", payload.operationId());
        variables.put("penaltyFailureReason", payload.reason());
        variables.put("sagaFailureReason", payload.reason());
        correlate(sagaId, "PENALTY_APPLICATION_FAILED", variables);
    }

    public void onPenaltyCounted(UUID sagaId, PenaltyCountedPayload payload) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("operationId", payload.operationId());
        variables.put("tenantId", payload.tenantId());
        variables.put("newPenaltyCount", payload.newPenaltyCount());
        correlate(sagaId, "PENALTY_COUNTED", variables);
    }

    public void onPenaltyRevoked(UUID sagaId, PenaltyRevokedPayload payload) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("operationId", payload.operationId());
        variables.put("tenantId", payload.tenantId());
        variables.put("penaltyWasApplied", payload.wasApplied());
        correlate(sagaId, "PENALTY_REVOKED", variables);
    }

    public void onPenaltyRevokeFailed(UUID sagaId, PenaltyRevokeFailedPayload payload) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("operationId", payload.operationId());
        variables.put("penaltyRevokeFailureReason", payload.reason());
        variables.put("sagaFailureReason", payload.reason());
        correlate(sagaId, "PENALTY_REVOKE_FAILED", variables);
    }

    public PenaltyApplicationSagaState describe(UUID sagaId) {
        ProcessInstance running = runtimeService.createProcessInstanceQuery()
                .variableValueEquals("sagaId", sagaId.toString())
                .singleResult();
        if (running != null) {
            Map<String, Object> variables = runtimeVariables(running.getProcessInstanceId());
            return toState(sagaId, variables, null, null);
        }

        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .variableValueEquals("sagaId", sagaId.toString())
                .singleResult();
        if (historic == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Saga not found: " + sagaId);
        }
        Map<String, Object> variables = historicVariables(historic.getId());
        return toState(sagaId, variables, instant(historic.getStartTime()), instant(historic.getEndTime()));
    }

    private void correlate(UUID sagaId, String messageName, Map<String, Object> variables) {
        try {
            MessageCorrelationBuilder builder = runtimeService.createMessageCorrelation(messageName)
                    .processInstanceVariableEquals("sagaId", sagaId.toString())
                    .setVariables(variables);
            builder.correlate();
        } catch (MismatchingMessageCorrelationException ignored) {
            // Kafka delivery is at-least-once. If a duplicate arrives after the BPMN
            // process already moved past this message event, it is safe to ignore here.
        }
    }

    private Map<String, Object> runtimeVariables(String processInstanceId) {
        List<VariableInstance> instances = runtimeService.createVariableInstanceQuery()
                .processInstanceIdIn(processInstanceId)
                .list();
        Map<String, Object> variables = new HashMap<>();
        for (VariableInstance instance : instances) {
            variables.put(instance.getName(), instance.getValue());
        }
        return variables;
    }

    private Map<String, Object> historicVariables(String processInstanceId) {
        List<HistoricVariableInstance> instances = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .list();
        Map<String, Object> variables = new HashMap<>();
        for (HistoricVariableInstance instance : instances) {
            variables.put(instance.getName(), instance.getValue());
        }
        return variables;
    }

    private PenaltyApplicationSagaState toState(UUID sagaId, Map<String, Object> variables,
                                                Instant processStartedAt, Instant processEndedAt) {
        return new PenaltyApplicationSagaState(
                sagaId,
                SagaType.PENALTY_APPLICATION,
                integer(variables.get("claimId")),
                sagaState(variables.get("sagaState")),
                integerOrDefault(variables.get("sagaAttemptCount"), 0),
                integerOrDefault(variables.get("sagaMaxAttempts"), 3),
                string(variables.get("sagaFailureReason")),
                instantOrDefault(variables.get("sagaStartedAt"), processStartedAt),
                instantOrDefault(variables.get("sagaLastEventAt"), null),
                instantOrDefault(variables.get("sagaCompletedAt"), processEndedAt)
        );
    }

    private SagaState sagaState(Object value) {
        if (value == null) {
            return SagaState.STARTED;
        }
        return SagaState.valueOf(value.toString());
    }

    private Integer integer(Object value) {
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return value == null ? null : Integer.valueOf(value.toString());
    }

    private int integerOrDefault(Object value, int fallback) {
        Integer integer = integer(value);
        return integer == null ? fallback : integer;
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }

    private Instant instantOrDefault(Object value, Instant fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return Instant.parse(value.toString());
    }

    private Instant instant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
