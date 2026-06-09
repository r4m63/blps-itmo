package blps.itmo.auth.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic identityEventsTopic() {
        return TopicBuilder.name(TopicNames.IDENTITY_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic penaltyEventsTopic() {
        return TopicBuilder.name(TopicNames.PENALTY_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                (record, ex) -> new TopicPartition(record.topic() + ".dlt", record.partition())
        );
        return new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 5L));
    }
}
