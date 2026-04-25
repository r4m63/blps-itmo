package blps.itmo.assessment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import blps.itmo.assessment.domain.AssessmentJob;
import blps.itmo.assessment.repository.AssessmentJobRepository;
import blps.itmo.platform.persistence.OutboxEvent;
import blps.itmo.platform.persistence.OutboxEventRepository;
import blps.itmo.platform.persistence.ProcessedMessage;
import blps.itmo.platform.persistence.ProcessedMessageRepository;

@SpringBootApplication(scanBasePackages = {"blps.itmo.assessment", "blps.itmo.platform"})
@EnableScheduling
@EntityScan(basePackageClasses = {AssessmentJob.class, OutboxEvent.class, ProcessedMessage.class})
@EnableJpaRepositories(basePackageClasses = {
        AssessmentJobRepository.class,
        OutboxEventRepository.class,
        ProcessedMessageRepository.class
})
public class AssessmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssessmentServiceApplication.class, args);
    }
}
