package blps.itmo.auth.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import blps.itmo.auth.domain.User;
import blps.itmo.auth.domain.UserRole;
import blps.itmo.auth.repository.UserRepository;
import blps.itmo.platform.events.EventEnvelope;
import blps.itmo.platform.events.EventType;
import blps.itmo.platform.events.RemoteUserView;
import blps.itmo.platform.events.payload.PenaltyAppliedPayload;
import blps.itmo.platform.events.payload.UserDeactivatedPayload;
import blps.itmo.platform.persistence.OutboxService;
import blps.itmo.platform.persistence.ProcessedMessageService;
import blps.itmo.platform.security.DemoJwtService;

@Service
public class AuthUserService {

    private static final String PENALTY_CONSUMER = "auth-service-penalty-applied";

    private final UserRepository userRepository;
    private final OutboxService outboxService;
    private final ProcessedMessageService processedMessageService;
    private final DemoJwtService jwtService;
    private final long jwtTtlSeconds;

    public AuthUserService(UserRepository userRepository,
            OutboxService outboxService,
            ProcessedMessageService processedMessageService,
            ObjectMapper objectMapper,
            @Value("${app.security.jwt-secret:lab3-demo-secret}") String jwtSecret,
            @Value("${app.security.jwt-ttl-seconds:86400}") long jwtTtlSeconds) {
        this.userRepository = userRepository;
        this.outboxService = outboxService;
        this.processedMessageService = processedMessageService;
        this.jwtService = new DemoJwtService(objectMapper, jwtSecret);
        this.jwtTtlSeconds = jwtTtlSeconds;
    }

    @Transactional(readOnly = true)
    public RemoteUserView getUserView(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        return RemoteUserView.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .enabled(user.isEnabled())
                .penaltyCount(user.getPenaltyCount())
                .build();
    }

    @Transactional(readOnly = true)
    public List<RemoteUserView> listUsers() {
        return userRepository.findAll().stream()
                .map(user -> RemoteUserView.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .role(user.getRole().name())
                        .enabled(user.isEnabled())
                        .penaltyCount(user.getPenaltyCount())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public LoginResult login(Long userId, String email) {
        User user = userId != null
                ? userRepository.findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId))
                : userRepository.findByEmail(email)
                        .orElseThrow(() -> new IllegalArgumentException("User not found by email: " + email));
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("User is disabled: " + user.getId());
        }
        return new LoginResult(
                jwtService.issue(user.getId(), user.getEmail(), user.getRole().name(), jwtTtlSeconds),
                toView(user));
    }

    @Transactional
    public RemoteUserView deactivateUser(Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setEnabled(false);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
        outboxService.record(
                EventType.USER_DEACTIVATED,
                "USER",
                userId,
                "user-deactivation-" + userId,
                "user-deactivation-" + userId,
                userId,
                UserDeactivatedPayload.builder()
                        .userId(userId)
                        .reason(reason == null || reason.isBlank() ? "Manual deactivation" : reason)
                        .build());
        return getUserView(userId);
    }

    @Transactional
    public void handlePenaltyApplied(EventEnvelope envelope, PenaltyAppliedPayload payload) {
        if (processedMessageService.isProcessed(envelope.getEventId(), PENALTY_CONSUMER)) {
            return;
        }
        User user = userRepository.findById(payload.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + payload.getTenantId()));
        user.setPenaltyCount(user.getPenaltyCount() + 1);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
        processedMessageService.markProcessed(envelope.getEventId(), PENALTY_CONSUMER, envelope.getCorrelationId());
    }

    public ApplicationRunner seedUsers() {
        return args -> {
            if (userRepository.count() > 0) {
                return;
            }
            OffsetDateTime now = OffsetDateTime.now();
            userRepository.saveAll(List.of(
                    User.builder().email("landlord1@example.com").role(UserRole.LANDLORD).createdAt(now)
                            .updatedAt(now).build(),
                    User.builder().email("landlord2@example.com").role(UserRole.LANDLORD).createdAt(now)
                            .updatedAt(now).build(),
                    User.builder().email("tenant1@example.com").role(UserRole.TENANT).createdAt(now).updatedAt(now)
                            .build(),
                    User.builder().email("tenant2@example.com").role(UserRole.TENANT).createdAt(now).updatedAt(now)
                            .build(),
                    User.builder().email("admin1@example.com").role(UserRole.ADMIN).createdAt(now).updatedAt(now)
                            .build(),
                    User.builder().email("admin2@example.com").role(UserRole.ADMIN).createdAt(now).updatedAt(now)
                            .build()));
        };
    }

    private RemoteUserView toView(User user) {
        return RemoteUserView.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .enabled(user.isEnabled())
                .penaltyCount(user.getPenaltyCount())
                .build();
    }

    public record LoginResult(String token, RemoteUserView user) {
    }
}
