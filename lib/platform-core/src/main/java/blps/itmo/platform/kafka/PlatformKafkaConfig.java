package blps.itmo.platform.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Конфигурация Kafka для всех консюмеров в проекте.
 *
 * Что делает:
 * 1. Настраивает автоматические ретраи при ошибках
 * 2. Отправляет заведомо "битые" сообщения в Dead Letter Topic (DLT)
 * 3. Предотвращает бесконечные циклы переобработки
 * 4. Не блокирует очередь "плохим" сообщением
 *
 * Пример:
 * ---------
 * Приходит сообщение в топик "claim.events"
 *   ↓
 * Consumer падает с ошибкой (например, не может десериализовать JSON)
 *   ↓
 * Повтор 1 (через 1 сек) → снова ошибка
 * Повтор 2 (через 1 сек) → снова ошибка
 * Повтор 3 (через 1 сек) → снова ошибка
 *   ↓
 * Сообщение уходит в "claim.events.dlt"
 *   ↓
 * Администратор позже смотрит DLT и решает, что делать
 */
@Configuration
public class PlatformKafkaConfig {

    /**
     * Создает фабрику консюмеров с обработчиком ошибок.
     *
     * @ConditionalOnBean(ConsumerFactory.class) — активируется только если
     * в контексте есть ConsumerFactory (то есть настроено подключение к Kafka)
     *
     * @param backoffMs пауза между ретраями (мс), по умолчанию 1000
     * @param maxAttempts максимальное число попыток (включая первую), по умолчанию 3
     */
    @Bean
    @ConditionalOnBean(ConsumerFactory.class)
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate,
            @Value("${app.kafka.retry.backoff-ms:1000}") long backoffMs,
            @Value("${app.kafka.retry.max-attempts:3}") long maxAttempts) {
        // 1. Создаем фабрику (она будет создавать экземпляры @KafkaListener)
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        // 2. Применяем стандартную конфигурацию Spring Boot
        configurer.configure(factory, consumerFactory);
        // 3. Создаем "восстановитель", который отправляет сообщения в DLT
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception exception) ->
                        new TopicPartition(record.topic() + ".dlt", record.partition()));
        // 4. Настраиваем обработчик ошибок:
        //    - recoverer: что делать после исчерпания ретраев (отправить в DLT)
        //    - FixedBackOff: стратегия пауз (фиксированная задержка, без увеличения)
        factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, new FixedBackOff(backoffMs, maxAttempts)));
        return factory;
    }

    // Как это работает на практике? -- загуглить
}
