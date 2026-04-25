package blps.itmo.repository.auth;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import blps.itmo.entity.auth.Privilege;
import blps.itmo.entity.auth.UserRole;

public interface PrivilegeRepository extends JpaRepository<Privilege, Long> {

    @Query(value = """
            SELECT p.code FROM public.privileges p
            JOIN public.role_privileges rp ON rp.privilege_id = p.id
            WHERE CAST(rp.role AS text) = :role
            """, nativeQuery = true)
    List<String> findPrivilegeCodesByRole(@Param("role") String role);

    default List<String> findPrivilegeCodesByRole(UserRole role) {
        return findPrivilegeCodesByRole(role.name());
    }
}
