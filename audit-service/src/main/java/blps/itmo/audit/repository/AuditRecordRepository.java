package blps.itmo.audit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import blps.itmo.audit.domain.AuditRecord;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {
    List<AuditRecord> findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(String aggregateType, String aggregateId);
}
