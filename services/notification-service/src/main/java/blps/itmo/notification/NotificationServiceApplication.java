package blps.itmo.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import blps.itmo.notification.domain.NotificationLog;
import blps.itmo.notification.repository.NotificationLogRepository;
import blps.itmo.platform.persistence.OutboxEvent;
import blps.itmo.platform.persistence.OutboxEventRepository;
import blps.itmo.platform.persistence.ProcessedMessage;
import blps.itmo.platform.persistence.ProcessedMessageRepository;

@SpringBootApplication(scanBasePackages = {"blps.itmo.notification", "blps.itmo.platform"})
@EnableScheduling
@EntityScan(basePackageClasses = {NotificationLog.class, OutboxEvent.class, ProcessedMessage.class})
@EnableJpaRepositories(basePackageClasses = {
        NotificationLogRepository.class,
        OutboxEventRepository.class,
        ProcessedMessageRepository.class
})
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
