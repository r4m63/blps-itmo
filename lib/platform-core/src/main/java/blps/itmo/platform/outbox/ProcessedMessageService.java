package blps.itmo.platform.outbox;

import java.time.Instant;

import blps.itmo.platform.outbox.domain.ProcessedMessage;
import blps.itmo.platform.outbox.domain.ProcessedMessageRepository;
import org.springframework.stereotype.Service;

/**
 * Сервис для работы с дедупликацией сообщений.
 *
 * Используется в Kafka-консьюмерах для идемпотентной обработки.
 *
 * Полный сценарий:
 * ================
 * 1. Consumer получает EventEnvelope из Kafka
 * 2. Проверяет processedService.isProcessed(eventId, consumerName)
 * 3. Если true — просто игнорирует (уже обработали в прошлый раз)
 * 4. Если false — выполняет бизнес-логику
 * 5. После успешной обработки вызывает markProcessed(...)
 *
 * Почему это безопасно?
 * - Даже если сообщение придет 2 раза (перебалансировка, рестарт consumer),
 *   второй раз обработка будет пропущена
 * - Используем БД как хранилище уже обработанных сообщений,
 *   потому что бизнес-логика тоже меняет БД — можем делать всё в одной транзакции
 */
@Service
public class ProcessedMessageService {

    private final ProcessedMessageRepository processedMessageRepository;

    public ProcessedMessageService(ProcessedMessageRepository processedMessageRepository) {
        this.processedMessageRepository = processedMessageRepository;
    }

    // Проверяет, обработано ли событие.
    public boolean isProcessed(String eventId, String consumerName) {
        return processedMessageRepository.existsByEventIdAndConsumerName(eventId, consumerName);
    }

    /**
     * Отмечает событие как обработанное.
     * Вызывается после успешного выполнения бизнес-логики.
     *
     * Важно: сохраняем и correlationId, чтобы можно было
     * трассировать запрос через всю систему даже при повторной обработке
     */
    public void markProcessed(String eventId, String consumerName, String correlationId) {
        processedMessageRepository.save(ProcessedMessage.builder()
                .eventId(eventId)
                .consumerName(consumerName)
                .correlationId(correlationId)
                .processedAt(Instant.now())
                .build());
    }
}
