package blps.itmo.claim.saga;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {

    Optional<SagaInstance> findByClaimIdAndSagaType(Integer claimId, SagaType sagaType);

    // Возвращает все саги в указанных состояниях, у которых last_event_at старше порога
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SagaInstance s where s.state in :states and s.lastEventAt < :threshold")
    List<SagaInstance> findStuck(@Param("states") List<SagaState> states,
                                 @Param("threshold") Instant threshold);
}
