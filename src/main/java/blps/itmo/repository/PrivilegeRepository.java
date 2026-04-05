package blps.itmo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import blps.itmo.entity.Privilege;
import blps.itmo.entity.UserRole;

public interface PrivilegeRepository extends JpaRepository<Privilege, Long> {

    /**
     * Возвращает коды привилегий, которые должны быть выданы пользователю
     * с указанной ролью. Источник — таблица {@code role_privileges}.
     */
    @Query(value = """
            SELECT p.code
            FROM privileges p
            JOIN role_privileges rp ON rp.privilege_id = p.id
            WHERE rp.role = CAST(:role AS userrole)
            """, nativeQuery = true)
    List<String> findPrivilegeCodesByRole(@Param("role") String role);

    default List<String> findPrivilegeCodesByRole(UserRole role) {
        return findPrivilegeCodesByRole(role.name());
    }
}
