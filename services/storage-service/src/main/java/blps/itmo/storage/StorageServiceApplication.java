package blps.itmo.storage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import blps.itmo.platform.outbox.domain.OutboxEvent;
import blps.itmo.platform.outbox.domain.OutboxEventRepository;
import blps.itmo.platform.outbox.domain.ProcessedMessage;
import blps.itmo.platform.outbox.domain.ProcessedMessageRepository;
import blps.itmo.storage.domain.Attachment;
import blps.itmo.storage.repository.AttachmentRepository;

@SpringBootApplication(scanBasePackages = {"blps.itmo.storage", "blps.itmo.platform"})
@EnableScheduling
@EntityScan(basePackageClasses = {Attachment.class, OutboxEvent.class, ProcessedMessage.class})
@EnableJpaRepositories(basePackageClasses = {
        AttachmentRepository.class,
        OutboxEventRepository.class,
        ProcessedMessageRepository.class
})
public class StorageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorageServiceApplication.class, args);
    }
}
