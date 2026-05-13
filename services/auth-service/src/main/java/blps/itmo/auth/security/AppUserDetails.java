package blps.itmo.auth.security;

import blps.itmo.auth.domain.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Getter
public class AppUserDetails implements UserDetails {

    private final User user;
    private final List<String> privilegeCodes;
    private final Collection<? extends GrantedAuthority> authorities;

    public AppUserDetails(User user, List<String> privilegeCodes) {
        this.user = user;
        this.privilegeCodes = List.copyOf(privilegeCodes);
        this.authorities = buildAuthorities(user, this.privilegeCodes);
    }

    private static Collection<? extends GrantedAuthority> buildAuthorities(User user, List<String> codes) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        codes.forEach(c -> authorities.add(new SimpleGrantedAuthority(c)));
        return Collections.unmodifiableList(authorities);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
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
        return user.isEnabled();
    }
}
