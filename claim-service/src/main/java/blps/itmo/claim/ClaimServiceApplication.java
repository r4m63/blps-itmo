package blps.itmo.claim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import blps.itmo.claim.domain.Claim;
import blps.itmo.claim.domain.ClaimTimelineEntry;
import blps.itmo.claim.repository.ClaimRepository;
import blps.itmo.claim.repository.ClaimTimelineRepository;
import blps.itmo.platform.persistence.OutboxEvent;
import blps.itmo.platform.persistence.OutboxEventRepository;
import blps.itmo.platform.persistence.ProcessedMessage;
import blps.itmo.platform.persistence.ProcessedMessageRepository;

@SpringBootApplication(scanBasePackages = {"blps.itmo.claim", "blps.itmo.platform"})
@EnableScheduling
@EntityScan(basePackageClasses = {Claim.class, ClaimTimelineEntry.class, OutboxEvent.class, ProcessedMessage.class})
@EnableJpaRepositories(basePackageClasses = {
        ClaimRepository.class,
        ClaimTimelineRepository.class,
        OutboxEventRepository.class,
        ProcessedMessageRepository.class
})
public class ClaimServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaimServiceApplication.class, args);
    }
}
