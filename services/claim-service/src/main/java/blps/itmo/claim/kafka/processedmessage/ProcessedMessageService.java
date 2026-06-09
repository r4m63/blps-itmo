package blps.itmo.claim.kafka.processedmessage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProcessedMessageService {

    private final ProcessedMessageRepository repository;

    @Transactional(readOnly = true)
    public boolean alreadyProcessed(UUID eventId) {
        return repository.existsById(eventId);
    }

    @Transactional
    public void markProcessed(UUID eventId) {
        ProcessedMessage msg = new ProcessedMessage();
        msg.setEventId(eventId);
        repository.save(msg);
    }
}
