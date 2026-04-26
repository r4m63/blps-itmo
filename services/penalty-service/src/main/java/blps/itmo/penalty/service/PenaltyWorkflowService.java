package blps.itmo.penalty.service;

import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import blps.itmo.penalty.domain.PenaltyOperation;
import blps.itmo.penalty.domain.PenaltyOperationStatus;
import blps.itmo.penalty.repository.PenaltyOperationRepository;
import blps.itmo.platform.events.EventEnvelope;
import blps.itmo.platform.events.EventType;
import blps.itmo.platform.events.payload.PenaltyApplicationFailedPayload;
import blps.itmo.platform.events.payload.PenaltyApplicationRequestedPayload;
import blps.itmo.platform.events.payload.PenaltyAppliedPayload;
import blps.itmo.platform.outbox.OutboxService;
import blps.itmo.platform.outbox.ProcessedMessageService;

@Service
public class PenaltyWorkflowService {

    private static final String REQUEST_CONSUMER = "penalty-service-requested";

    private final PenaltyOperationRepository penaltyOperationRepository;
    private final ProcessedMessageService processedMessageService;
    private final OutboxService outboxService;
    private final long timeoutMinutes;

    public PenaltyWorkflowService(PenaltyOperationRepository penaltyOperationRepository,
            ProcessedMessageService processedMessageService,
            OutboxService outboxService,
            @Value("${app.penalty.timeout-minutes:30}") long timeoutMinutes) {
        this.penaltyOperationRepository = penaltyOperationRepository;
        this.processedMessageService = processedMessageService;
        this.outboxService = outboxService;
        this.timeoutMinutes = timeoutMinutes;
    }

    @Transactional
    public void handlePenaltyRequested(EventEnvelope envelope, PenaltyApplicationRequestedPayload payload) {
        if (processedMessageService.isProcessed(envelope.getEventId(), REQUEST_CONSUMER)) {
            return;
        }
        penaltyOperationRepository.save(PenaltyOperation.builder()
                .claimId(payload.getClaimId())
                .tenantId(payload.getTenantId())
                .correlationId(envelope.getCorrelationId())
                .penaltyAmount(payload.getPenaltyAmount())
                .penaltyCurrency(payload.getPenaltyCurrency())
                .simulateFailure(payload.isSimulateFailure())
                .reason(payload.getNote())
                .status(PenaltyOperationStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build());
        processedMessageService.markProcessed(envelope.getEventId(), REQUEST_CONSUMER, envelope.getCorrelationId());
    }

    @Scheduled(fixedDelayString = "${app.penalty.poll-interval-ms:2000}")
    @Transactional
    public void processPendingOperations() {
        for (PenaltyOperation operation : penaltyOperationRepository.findTop20ByStatusOrderByCreatedAtAsc(
                PenaltyOperationStatus.PENDING)) {
            operation.setStatus(PenaltyOperationStatus.PROCESSING);
            penaltyOperationRepository.save(operation);
            if (operation.isSimulateFailure()) {
                operation.setStatus(PenaltyOperationStatus.FAILED);
                operation.setProcessedAt(OffsetDateTime.now());
                penaltyOperationRepository.save(operation);
                outboxService.record(
                        EventType.PENALTY_APPLICATION_FAILED,
                        "CLAIM",
                        operation.getClaimId(),
                        operation.getCorrelationId(),
                        "penalty-application-" + operation.getClaimId(),
                        operation.getTenantId(),
                        PenaltyApplicationFailedPayload.builder()
                                .claimId(operation.getClaimId())
                                .tenantId(operation.getTenantId())
                                .operationId(operation.getId())
                                .reason("Penalty application failed in external processor simulation")
                                .build());
                continue;
            }
            operation.setStatus(PenaltyOperationStatus.APPLIED);
            operation.setProcessedAt(OffsetDateTime.now());
            penaltyOperationRepository.save(operation);
            outboxService.record(
                    EventType.PENALTY_APPLIED,
                    "CLAIM",
                    operation.getClaimId(),
                    operation.getCorrelationId(),
                    "penalty-application-" + operation.getClaimId(),
                    operation.getTenantId(),
                    PenaltyAppliedPayload.builder()
                            .claimId(operation.getClaimId())
                            .tenantId(operation.getTenantId())
                            .penaltyAmount(operation.getPenaltyAmount())
                            .penaltyCurrency(operation.getPenaltyCurrency())
                            .operationId(operation.getId())
                            .build());
        }
    }

    @Scheduled(fixedDelayString = "${app.penalty.timeout-poll-interval-ms:60000}")
    @Transactional
    public void failTimedOutOperations() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(timeoutMinutes);
        for (PenaltyOperation operation : penaltyOperationRepository.findByStatusAndCreatedAtBefore(
                PenaltyOperationStatus.PROCESSING, threshold)) {
            operation.setStatus(PenaltyOperationStatus.FAILED);
            operation.setProcessedAt(OffsetDateTime.now());
            operation.setReason("Penalty operation timed out");
            penaltyOperationRepository.save(operation);
            outboxService.record(
                    EventType.PENALTY_APPLICATION_FAILED,
                    "CLAIM",
                    operation.getClaimId(),
                    operation.getCorrelationId(),
                    "penalty-application-" + operation.getClaimId(),
                    operation.getTenantId(),
                    PenaltyApplicationFailedPayload.builder()
                            .claimId(operation.getClaimId())
                            .tenantId(operation.getTenantId())
                            .operationId(operation.getId())
                            .reason(operation.getReason())
                            .build());
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<PenaltyOperation> getOperationsForClaim(Long claimId) {
        return penaltyOperationRepository.findByClaimIdOrderByCreatedAtDesc(claimId);
    }

    @Transactional
    public PenaltyOperation retryFailedOperation(Long operationId) {
        PenaltyOperation operation = penaltyOperationRepository.findById(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Penalty operation not found: " + operationId));
        if (operation.getStatus() != PenaltyOperationStatus.FAILED) {
            throw new IllegalStateException("Only FAILED penalty operations can be retried");
        }
        operation.setStatus(PenaltyOperationStatus.PENDING);
        operation.setSimulateFailure(false);
        operation.setReason("Retried manually after failure");
        operation.setProcessedAt(null);
        return penaltyOperationRepository.save(operation);
    }
}
