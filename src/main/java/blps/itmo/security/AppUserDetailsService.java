package blps.itmo.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import blps.itmo.entity.User;
import blps.itmo.repository.PrivilegeRepository;
import blps.itmo.repository.UserRepository;

/**
 * Загружает учётную запись пользователя из реляционной БД и собирает
 * набор {@link GrantedAuthority} из таблиц {@code privileges} и
 * {@code role_privileges}.
 * <p>
 * В authority добавляются:
 * <ul>
 *   <li>роль в формате {@code ROLE_<NAME>} — для совместимости с
 *       {@code hasRole()} / {@code hasAnyRole()};</li>
 *   <li>отдельные коды привилегий — для точечного контроля через
 *       {@code hasAuthority('...')}.</li>
 * </ul>
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PrivilegeRepository privilegeRepository;

    public AppUserDetailsService(UserRepository userRepository, PrivilegeRepository privilegeRepository) {
        this.userRepository = userRepository;
        this.privilegeRepository = privilegeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<String> privilegeCodes = privilegeRepository.findPrivilegeCodesByRole(user.getRole());

        List<GrantedAuthority> authorities = new ArrayList<>(privilegeCodes.size() + 1);
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        for (String code : privilegeCodes) {
            authorities.add(new SimpleGrantedAuthority(code));
        }

        return new AppUserPrincipal(user, authorities);
    }
}
