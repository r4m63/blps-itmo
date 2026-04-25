package blps.itmo.security;

import java.util.Collection;
import java.util.Collections;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import blps.itmo.entity.auth.User;
import blps.itmo.entity.auth.UserRole;

/**
 * Кастомная обёртка {@link UserDetails}, которая помимо стандартных полей
 * Spring Security несёт идентификатор пользователя из БД и его бизнес-роль.
 * <p>
 * Использование: в контроллерах через {@code @AuthenticationPrincipal AppUserPrincipal}
 * — позволяет получить {@code userId} без дополнительных обращений к репозиторию
 * и без риска доверять идентификатору, присланному клиентом.
 */
public class AppUserPrincipal implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String email;
    private final String passwordHash;
    private final UserRole role;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public AppUserPrincipal(User user, Collection<? extends GrantedAuthority> authorities) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.enabled = Boolean.TRUE.equals(user.getEnabled());
        this.authorities = authorities == null ? Collections.emptyList() : authorities;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public UserRole getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
