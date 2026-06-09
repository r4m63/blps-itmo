package blps.itmo.claim.process;

import org.camunda.bpm.engine.delegate.DelegateExecution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

final class ProcessVariables {

    private ProcessVariables() {
    }

    static Integer integer(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return value == null ? null : Integer.valueOf(value.toString());
    }

    static boolean bool(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    static String string(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        return value == null ? null : value.toString();
    }

    static BigDecimal decimal(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        if (value instanceof BigDecimal b) {
            return b;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return value == null ? null : new BigDecimal(value.toString());
    }

    static UUID uuid(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        if (value instanceof UUID u) {
            return u;
        }
        return value == null ? null : UUID.fromString(value.toString());
    }

    static void state(DelegateExecution execution, String state) {
        Instant now = Instant.now();
        if (execution.getVariable("sagaStartedAt") == null) {
            execution.setVariable("sagaStartedAt", now.toString());
        }
        execution.setVariable("sagaState", state);
        execution.setVariable("sagaLastEventAt", now.toString());
    }

    static void terminalState(DelegateExecution execution, String state) {
        state(execution, state);
        execution.setVariable("sagaCompletedAt", Instant.now().toString());
    }
}
