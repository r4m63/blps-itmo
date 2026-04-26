package blps.itmo.penalty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import blps.itmo.penalty.domain.PenaltyOperation;
import blps.itmo.penalty.repository.PenaltyOperationRepository;
import blps.itmo.platform.outbox.domain.OutboxEvent;
import blps.itmo.platform.outbox.domain.OutboxEventRepository;
import blps.itmo.platform.outbox.domain.ProcessedMessage;
import blps.itmo.platform.outbox.domain.ProcessedMessageRepository;

@SpringBootApplication(scanBasePackages = {"blps.itmo.penalty", "blps.itmo.platform"})
@EnableScheduling
@EntityScan(basePackageClasses = {PenaltyOperation.class, OutboxEvent.class, ProcessedMessage.class})
@EnableJpaRepositories(basePackageClasses = {
        PenaltyOperationRepository.class,
        OutboxEventRepository.class,
        ProcessedMessageRepository.class
})
public class PenaltyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PenaltyServiceApplication.class, args);
    }
}
