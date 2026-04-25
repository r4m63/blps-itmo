package blps.itmo.notification.service;

import java.time.OffsetDateTime;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import blps.itmo.notification.domain.NotificationLog;
import blps.itmo.notification.repository.NotificationLogRepository;
import blps.itmo.platform.events.EventEnvelope;
import blps.itmo.platform.events.TopicNames;
import blps.itmo.platform.persistence.ProcessedMessageService;

@Component
public class NotificationEventListener {

    private static final String CONSUMER_NAME = "notification-service";

    private final ObjectMapper objectMapper;
    private final NotificationLogRepository notificationLogRepository;
    private final ProcessedMessageService processedMessageService;

    public NotificationEventListener(ObjectMapper objectMapper,
            NotificationLogRepository notificationLogRepository,
            ProcessedMessageService processedMessageService) {
        this.objectMapper = objectMapper;
        this.notificationLogRepository = notificationLogRepository;
        this.processedMessageService = processedMessageService;
    }

    @KafkaListener(topics = {
            TopicNames.CLAIM_EVENTS,
            TopicNames.ASSESSMENT_EVENTS,
            TopicNames.PENALTY_EVENTS,
            TopicNames.AUTH_EVENTS
    }, groupId = "notification-service")
    @Transactional
    public void onEvent(String rawEvent) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(rawEvent, EventEnvelope.class);
        if (processedMessageService.isProcessed(envelope.getEventId(), CONSUMER_NAME)) {
            return;
        }
        notificationLogRepository.save(NotificationLog.builder()
                .eventId(envelope.getEventId())
                .eventType(envelope.getEventType().name())
                .aggregateId(envelope.getAggregateId())
                .message("Notify stakeholders about " + envelope.getEventType().name()
                        + " for aggregate " + envelope.getAggregateId())
                .createdAt(OffsetDateTime.now())
                .build());
        processedMessageService.markProcessed(envelope.getEventId(), CONSUMER_NAME, envelope.getCorrelationId());
    }
}
