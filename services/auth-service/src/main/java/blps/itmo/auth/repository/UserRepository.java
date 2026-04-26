package blps.itmo.auth.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import blps.itmo.auth.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByEnabledTrueOrderByIdAsc();

    Optional<User> findByEmail(String email);
}
