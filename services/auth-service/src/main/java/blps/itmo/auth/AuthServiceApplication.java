package blps.itmo.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import blps.itmo.auth.domain.User;
import blps.itmo.auth.repository.UserRepository;
import blps.itmo.platform.persistence.OutboxEvent;
import blps.itmo.platform.persistence.OutboxEventRepository;
import blps.itmo.platform.persistence.ProcessedMessage;
import blps.itmo.platform.persistence.ProcessedMessageRepository;

@SpringBootApplication(scanBasePackages = {"blps.itmo.auth", "blps.itmo.platform"})
@EnableScheduling
@EntityScan(basePackageClasses = {User.class, OutboxEvent.class, ProcessedMessage.class})
@EnableJpaRepositories(basePackageClasses = {
        UserRepository.class,
        OutboxEventRepository.class,
        ProcessedMessageRepository.class
})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
