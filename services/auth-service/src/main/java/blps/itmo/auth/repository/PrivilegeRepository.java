package blps.itmo.auth.repository;

import blps.itmo.auth.domain.Privilege;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrivilegeRepository extends JpaRepository<Privilege, Integer> {

    @Query(value = """
            SELECT p.code FROM privileges p
            JOIN role_privileges rp ON rp.privilege_id = p.id
            WHERE rp.role = CAST(:role AS userrole)
            """, nativeQuery = true)
    List<String> findCodesByRole(@Param("role") String role);
}
