package blps.itmo.platform.outbox.domain;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import blps.itmo.platform.events.EventType;

// Совместимость с PostgreSQL ENUM типами.
@Component
@ConditionalOnBean(JdbcTemplate.class)
public class PostgresEnumCompatibilityRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public PostgresEnumCompatibilityRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        addValuesIfTypeExists("outbox_event_type",
                java.util.Arrays.stream(EventType.values()).map(Enum::name).toList());
        addValuesIfTypeExists("outbox_status",
                java.util.Arrays.stream(OutboxStatus.values()).map(Enum::name).toList());
        addValuesIfTypeExists("claim_status", List.of(
                "ASSESSMENT_IN_PROGRESS",
                "ASSESSMENT_FAILED",
                "MANUAL_REVIEW_REQUIRED",
                "NEED_ADDITIONAL_INFO",
                "AWAITING_TENANT_RESPONSE",
                "SUPPORT_REVIEW",
                "PENALTY_PROCESSING",
                "PENALTY_APPLIED",
                "PENALTY_PROCESSING_FAILED",
                "CLOSED_NO_PENALTY"));
        addValuesIfTypeExists("attachment_status", List.of(
                "INITIALIZED",
                "CONFIRMED",
                "BINDING_REQUESTED",
                "BOUND",
                "BINDING_FAILED"));
    }

    private void addValuesIfTypeExists(String typeName, List<String> values) {
        Boolean typeExists = jdbcTemplate.queryForObject(
                "select exists (select 1 from pg_type where typname = ?)",
                Boolean.class,
                typeName);
        if (!Boolean.TRUE.equals(typeExists)) {
            return;
        }
        for (String value : values) {
            jdbcTemplate.execute("alter type " + typeName + " add value if not exists '" + value + "'");
        }
    }
}
