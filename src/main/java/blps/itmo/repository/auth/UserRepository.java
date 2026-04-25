package blps.itmo.repository.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import blps.itmo.entity.auth.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
