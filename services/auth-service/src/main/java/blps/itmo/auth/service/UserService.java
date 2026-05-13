package blps.itmo.auth.service;

import blps.itmo.auth.api.dto.CreateUserRequest;
import blps.itmo.auth.api.dto.RegisterRequest;
import blps.itmo.auth.domain.User;
import blps.itmo.auth.domain.UserRole;
import blps.itmo.auth.repository.PrivilegeRepository;
import blps.itmo.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PrivilegeRepository privilegeRepository;
    private final PasswordEncoder passwordEncoder;

    public User getById(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
    }

    public List<User> listAll() {
        return userRepository.findAll();
    }

    public List<String> privilegesOf(UserRole role) {
        return privilegeRepository.findCodesByRole(role.name());
    }

    @Transactional
    public User register(RegisterRequest req) {
        if (req.role() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admin role cannot be self-registered");
        }
        return createUser(req.email(), req.password(), req.role());
    }

    @Transactional
    public User createByAdmin(CreateUserRequest req) {
        return createUser(req.email(), req.password(), req.role());
    }

    @Transactional
    public User deactivate(Integer id) {
        User user = getById(id);
        if (!user.isEnabled()) {
            return user;
        }
        user.setEnabled(false);
        return userRepository.save(user);
    }

    @Transactional
    public User incrementPenaltyCount(Integer id) {
        User user = getById(id);
        user.setPenaltyCount(user.getPenaltyCount() + 1);
        return userRepository.save(user);
    }

    private User createUser(String email, String rawPassword, UserRole role) {
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already in use: " + email);
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setEnabled(true);
        user.setPenaltyCount(0);
        return userRepository.save(user);
    }
}
