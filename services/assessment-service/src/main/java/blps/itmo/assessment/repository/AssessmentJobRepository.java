package blps.itmo.assessment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import blps.itmo.assessment.domain.AssessmentJob;
import blps.itmo.assessment.domain.AssessmentJobStatus;

public interface AssessmentJobRepository extends JpaRepository<AssessmentJob, Long> {
    List<AssessmentJob> findTop20ByStatusOrderByCreatedAtAsc(AssessmentJobStatus status);
}
