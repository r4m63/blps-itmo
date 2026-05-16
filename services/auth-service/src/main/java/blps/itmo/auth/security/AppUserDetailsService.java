package blps.itmo.auth.security;

import blps.itmo.auth.domain.User;
import blps.itmo.auth.repository.PrivilegeRepository;
import blps.itmo.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PrivilegeRepository privilegeRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.info(">>> loadUserByUsername called with email='{}'", email);
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> {
                        log.warn(">>> findByEmail returned empty for '{}'", email);
                        return new UsernameNotFoundException("User not found: " + email);
                    });
            log.info(">>> loaded user id={} email={} role={} enabled={} hashPrefix={}",
                    user.getId(), user.getEmail(), user.getRole(), user.isEnabled(),
                    user.getPasswordHash() == null ? "null" : user.getPasswordHash().substring(0, Math.min(7, user.getPasswordHash().length())));
            List<String> codes = privilegeRepository.findCodesByRole(user.getRole().name());
            log.info(">>> privileges for {} = {}", user.getRole(), codes);
            return new AppUserDetails(user, codes);
        } catch (RuntimeException e) {
            log.error(">>> loadUserByUsername FAILED for '{}': {}", email, e.toString(), e);
            throw e;
        }
    }
}
