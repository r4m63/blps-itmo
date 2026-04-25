package blps.itmo.penalty.service;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import blps.itmo.penalty.domain.PenaltyOperation;

@RestController
@RequestMapping("/api/penalties")
public class PenaltyController {

    private final PenaltyWorkflowService penaltyWorkflowService;

    public PenaltyController(PenaltyWorkflowService penaltyWorkflowService) {
        this.penaltyWorkflowService = penaltyWorkflowService;
    }

    @GetMapping("/claims/{claimId}")
    public List<PenaltyOperation> getPenaltyOperations(@PathVariable Long claimId) {
        return penaltyWorkflowService.getOperationsForClaim(claimId);
    }

    @PostMapping("/operations/{operationId}/retry")
    public PenaltyOperation retryFailedOperation(@PathVariable Long operationId) {
        return penaltyWorkflowService.retryFailedOperation(operationId);
    }
}
