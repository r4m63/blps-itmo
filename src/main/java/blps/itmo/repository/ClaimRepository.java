package blps.itmo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import blps.itmo.entity.Claim;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
}
