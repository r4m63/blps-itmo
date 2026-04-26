package blps.itmo.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import blps.itmo.audit.domain.AuditRecord;
import blps.itmo.audit.repository.AuditRecordRepository;
import blps.itmo.platform.outbox.domain.OutboxEvent;
import blps.itmo.platform.outbox.domain.OutboxEventRepository;
import blps.itmo.platform.outbox.domain.ProcessedMessage;
import blps.itmo.platform.outbox.domain.ProcessedMessageRepository;

@SpringBootApplication(scanBasePackages = {"blps.itmo.audit", "blps.itmo.platform"})
@EnableScheduling
@EntityScan(basePackageClasses = {AuditRecord.class, OutboxEvent.class, ProcessedMessage.class})
@EnableJpaRepositories(basePackageClasses = {
        AuditRecordRepository.class,
        OutboxEventRepository.class,
        ProcessedMessageRepository.class
})
public class AuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
