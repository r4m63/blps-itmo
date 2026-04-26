package blps.itmo.assessment.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import blps.itmo.assessment.domain.AssessmentJob;
import blps.itmo.assessment.domain.AssessmentJobStatus;
import blps.itmo.assessment.repository.AssessmentJobRepository;
import blps.itmo.platform.events.EventEnvelope;
import blps.itmo.platform.events.EventType;
import blps.itmo.platform.events.payload.AdditionalInfoProvidedPayload;
import blps.itmo.platform.events.payload.AssessmentCompletedPayload;
import blps.itmo.platform.events.payload.AssessmentFailedPayload;
import blps.itmo.platform.events.payload.ClaimCreatedPayload;
import blps.itmo.platform.persistence.OutboxService;
import blps.itmo.platform.persistence.ProcessedMessageService;

@Service
public class AssessmentWorkflowService {

    private static final String CLAIM_CONSUMER = "assessment-service-claim-events";

    private final AssessmentJobRepository assessmentJobRepository;
    private final ProcessedMessageService processedMessageService;
    private final OutboxService outboxService;
    private final long timeoutMinutes;

    public AssessmentWorkflowService(AssessmentJobRepository assessmentJobRepository,
            ProcessedMessageService processedMessageService,
            OutboxService outboxService,
            @Value("${app.assessment.timeout-minutes:30}") long timeoutMinutes) {
        this.assessmentJobRepository = assessmentJobRepository;
        this.processedMessageService = processedMessageService;
        this.outboxService = outboxService;
        this.timeoutMinutes = timeoutMinutes;
    }

    @Transactional
    public void handleClaimCreated(EventEnvelope envelope, ClaimCreatedPayload payload) {
        if (processedMessageService.isProcessed(envelope.getEventId(), CLAIM_CONSUMER)) {
            return;
        }
        assessmentJobRepository.save(AssessmentJob.builder()
                .claimId(payload.getClaimId())
                .correlationId(envelope.getCorrelationId())
                .landlordId(payload.getLandlordId())
                .tenantId(payload.getTenantId())
                .claimedAmount(payload.getClaimedAmount())
                .currency(payload.getCurrency())
                .status(AssessmentJobStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build());
        processedMessageService.markProcessed(envelope.getEventId(), CLAIM_CONSUMER, envelope.getCorrelationId());
    }

    @Transactional
    public void handleAdditionalInfo(EventEnvelope envelope, AdditionalInfoProvidedPayload payload) {
        if (processedMessageService.isProcessed(envelope.getEventId(), CLAIM_CONSUMER)) {
            return;
        }
        assessmentJobRepository.save(AssessmentJob.builder()
                .claimId(payload.getClaimId())
                .correlationId(envelope.getCorrelationId())
                .claimedAmount(payload.getClaimedAmount())
                .currency(payload.getCurrency())
                .comment(payload.getComment())
                .attemptNo(2)
                .status(AssessmentJobStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build());
        processedMessageService.markProcessed(envelope.getEventId(), CLAIM_CONSUMER, envelope.getCorrelationId());
    }

    @Scheduled(fixedDelayString = "${app.assessment.poll-interval-ms:2000}")
    @Transactional
    public void processPendingJobs() {
        for (AssessmentJob job : assessmentJobRepository.findTop20ByStatusOrderByCreatedAtAsc(AssessmentJobStatus.PENDING)) {
            job.setStatus(AssessmentJobStatus.PROCESSING);
            assessmentJobRepository.save(job);

            boolean requiresAdditionalInfo = job.getAttemptNo() == 1 && job.getClaimedAmount().compareTo(new BigDecimal("200")) >= 0;
            boolean penaltyGrounds = !requiresAdditionalInfo
                    && job.getClaimedAmount().compareTo(new BigDecimal("50")) >= 0;
            BigDecimal assessmentAmount = penaltyGrounds
                    ? job.getClaimedAmount().multiply(new BigDecimal("0.70")).setScale(2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);

            String note = requiresAdditionalInfo
                    ? "Additional materials are required before penalty decision"
                    : penaltyGrounds
                            ? "Assessment completed asynchronously and penalty grounds were detected"
                            : "Assessment completed asynchronously and penalty grounds were not detected";

            outboxService.record(
                    EventType.ASSESSMENT_COMPLETED,
                    "CLAIM",
                    job.getClaimId(),
                    job.getCorrelationId(),
                    "claim-lifecycle-" + job.getClaimId(),
                    job.getLandlordId(),
                    AssessmentCompletedPayload.builder()
                            .claimId(job.getClaimId())
                            .assessmentAmount(assessmentAmount)
                            .assessmentNotes(note)
                            .penaltyGrounds(penaltyGrounds)
                            .requiresAdditionalInfo(requiresAdditionalInfo)
                            .build());

            job.setStatus(AssessmentJobStatus.COMPLETED);
            job.setProcessedAt(OffsetDateTime.now());
            assessmentJobRepository.save(job);
        }
    }

    @Scheduled(fixedDelayString = "${app.assessment.timeout-poll-interval-ms:60000}")
    @Transactional
    public void failTimedOutJobs() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(timeoutMinutes);
        for (AssessmentJob job : assessmentJobRepository.findByStatusAndCreatedAtBefore(AssessmentJobStatus.PROCESSING, threshold)) {
            job.setStatus(AssessmentJobStatus.FAILED);
            job.setProcessedAt(OffsetDateTime.now());
            assessmentJobRepository.save(job);
            outboxService.record(
                    EventType.ASSESSMENT_FAILED,
                    "CLAIM",
                    job.getClaimId(),
                    job.getCorrelationId(),
                    "claim-lifecycle-" + job.getClaimId(),
                    job.getLandlordId(),
                    AssessmentFailedPayload.builder()
                            .claimId(job.getClaimId())
                            .jobId(job.getId())
                            .reason("Assessment job timed out")
                            .build());
        }
    }
}
