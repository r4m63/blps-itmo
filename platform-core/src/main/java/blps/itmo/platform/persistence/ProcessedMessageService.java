package blps.itmo.platform.persistence;

import java.time.Instant;

import org.springframework.stereotype.Service;

@Service
public class ProcessedMessageService {

    private final ProcessedMessageRepository processedMessageRepository;

    public ProcessedMessageService(ProcessedMessageRepository processedMessageRepository) {
        this.processedMessageRepository = processedMessageRepository;
    }

    public boolean isProcessed(String eventId, String consumerName) {
        return processedMessageRepository.existsByEventIdAndConsumerName(eventId, consumerName);
    }

    public void markProcessed(String eventId, String consumerName, String correlationId) {
        processedMessageRepository.save(ProcessedMessage.builder()
                .eventId(eventId)
                .consumerName(consumerName)
                .correlationId(correlationId)
                .processedAt(Instant.now())
                .build());
    }
}
