package blps.itmo.service;

import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import blps.itmo.entity.auth.User;
import blps.itmo.entity.auth.UserRole;
import blps.itmo.exception.BadRequestException;
import blps.itmo.exception.ResourceNotFoundException;
import blps.itmo.repository.auth.UserRepository;

@Service
public class AuthUserService {

    private final UserRepository userRepository;
    private final TransactionTemplate txTemplate;
    private final TransactionTemplate readOnlyTxTemplate;

    public AuthUserService(UserRepository userRepository,
            @Qualifier("jtaTransactionTemplate") TransactionTemplate txTemplate,
            @Qualifier("jtaReadOnlyTransactionTemplate") TransactionTemplate readOnlyTxTemplate) {
        this.userRepository = userRepository;
        this.txTemplate = txTemplate;
        this.readOnlyTxTemplate = readOnlyTxTemplate;
    }

    public User requireUser(Long userId) {
        return readOnlyTxTemplate.execute(status -> userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of(User.class, "id", userId)));
    }

    public User requireUserWithRole(Long userId, UserRole expectedRole, String message) {
        return readOnlyTxTemplate.execute(status -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> ResourceNotFoundException.of(User.class, "id", userId));
            if (user.getRole() != expectedRole) {
                throw new BadRequestException(message);
            }
            if (!Boolean.TRUE.equals(user.getEnabled())) {
                throw new BadRequestException("User is disabled: " + user.getEmail());
            }
            return user;
        });
    }

    public User deactivateUser(Long userId) {
        return txTemplate.execute(status -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> ResourceNotFoundException.of(User.class, "id", userId));
            if (Boolean.TRUE.equals(user.getEnabled())) {
                user.setEnabled(false);
                user.setUpdatedAt(OffsetDateTime.now());
                userRepository.save(user);
            }
            return user;
        });
    }

    public void incrementPenaltyCount(Long userId) {
        txTemplate.executeWithoutResult(status -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> ResourceNotFoundException.of(User.class, "id", userId));
            Integer currentCount = user.getPenaltyCount() == null ? 0 : user.getPenaltyCount();
            user.setPenaltyCount(currentCount + 1);
            user.setUpdatedAt(OffsetDateTime.now());
            userRepository.save(user);
        });
    }
}
