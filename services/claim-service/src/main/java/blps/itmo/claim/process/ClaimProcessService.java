package blps.itmo.claim.process;

import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClaimProcessService {

    public static final String PROCESS_KEY = "claimProcess";

    public static final String TASK_INTAKE_START = "task_intake_start";
    public static final String TASK_INTAKE_DECISION = "task_intake_decision";
    public static final String TASK_ADDITIONAL_INFO = "task_additional_info";
    public static final String TASK_ASSESSMENT = "task_assessment";
    public static final String TASK_TENANT_RESPONSE = "task_tenant_response";
    public static final String TASK_SUPPORT_DECISION = "task_support_decision";

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final ClaimProcessProperties properties;

    public void start(Integer claimId, Integer landlordId, Integer tenantId) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("claimId", claimId);
        variables.put("landlordId", landlordId);
        variables.put("tenantId", tenantId);
        variables.put("sagaStartedHold", properties.getTimers().getStartedHold());
        variables.put("tenantResponseTimeout", properties.getTimers().getTenantResponseTimeout());
        variables.put("awaitingPenaltyAppliedTimeout", properties.getTimers().getAwaitingPenaltyApplied());
        variables.put("awaitingPenaltyCountedTimeout", properties.getTimers().getAwaitingPenaltyCounted());
        variables.put("compensatingRetryTimeout", properties.getTimers().getCompensatingRetry());
        variables.put("sagaMaxAttempts", 3);
        runtimeService.startProcessInstanceByKey(PROCESS_KEY, claimId.toString(), variables);
    }

    public void complete(Integer claimId, String taskDefinitionKey, Integer userId, Map<String, Object> variables) {
        Task task = taskService.createTaskQuery()
                .processDefinitionKey(PROCESS_KEY)
                .processInstanceBusinessKey(claimId.toString())
                .taskDefinitionKey(taskDefinitionKey)
                .active()
                .singleResult();
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No active Camunda task %s for claim %s".formatted(taskDefinitionKey, claimId));
        }
        taskService.setAssignee(task.getId(), userId.toString());
        taskService.complete(task.getId(), variables);
    }

    public ProcessInstance getProcessInstance(Integer claimId) {
        return runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(PROCESS_KEY)
                .processInstanceBusinessKey(claimId.toString())
                .singleResult();
    }
}
