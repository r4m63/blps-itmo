package blps.itmo.audit.service;

import java.time.OffsetDateTime;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import blps.itmo.audit.domain.AuditRecord;
import blps.itmo.audit.repository.AuditRecordRepository;
import blps.itmo.platform.events.EventEnvelope;
import blps.itmo.platform.events.TopicNames;
import blps.itmo.platform.persistence.ProcessedMessageService;

@Component
public class AuditEventListener {

    private static final String CONSUMER_NAME = "audit-service";

    private final ObjectMapper objectMapper;
    private final AuditRecordRepository auditRecordRepository;
    private final ProcessedMessageService processedMessageService;

    public AuditEventListener(ObjectMapper objectMapper,
            AuditRecordRepository auditRecordRepository,
            ProcessedMessageService processedMessageService) {
        this.objectMapper = objectMapper;
        this.auditRecordRepository = auditRecordRepository;
        this.processedMessageService = processedMessageService;
    }

    @KafkaListener(topics = {
            TopicNames.CLAIM_EVENTS,
            TopicNames.ASSESSMENT_EVENTS,
            TopicNames.PENALTY_EVENTS,
            TopicNames.AUTH_EVENTS,
            TopicNames.STORAGE_EVENTS
    }, groupId = "audit-service")
    @Transactional
    public void onEvent(String rawEvent) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(rawEvent, EventEnvelope.class);
        if (processedMessageService.isProcessed(envelope.getEventId(), CONSUMER_NAME)) {
            return;
        }
        auditRecordRepository.save(AuditRecord.builder()
                .eventId(envelope.getEventId())
                .eventType(envelope.getEventType().name())
                .aggregateType(envelope.getAggregateType())
                .aggregateId(envelope.getAggregateId())
                .correlationId(envelope.getCorrelationId())
                .sagaId(envelope.getSagaId())
                .payloadJson(objectMapper.writeValueAsString(envelope.getPayload()))
                .createdAt(OffsetDateTime.now())
                .build());
        processedMessageService.markProcessed(envelope.getEventId(), CONSUMER_NAME, envelope.getCorrelationId());
    }
}
