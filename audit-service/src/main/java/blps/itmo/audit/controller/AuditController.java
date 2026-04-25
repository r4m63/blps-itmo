package blps.itmo.audit.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import blps.itmo.audit.domain.AuditRecord;
import blps.itmo.audit.repository.AuditRecordRepository;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditRecordRepository auditRecordRepository;

    public AuditController(AuditRecordRepository auditRecordRepository) {
        this.auditRecordRepository = auditRecordRepository;
    }

    @GetMapping("/claims/{claimId}/events")
    public List<AuditRecord> getClaimEvents(@PathVariable Long claimId) {
        return auditRecordRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("CLAIM", String.valueOf(claimId));
    }
}
