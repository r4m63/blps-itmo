package blps.itmo.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import blps.itmo.dto.DeactivateUserResponse;
import blps.itmo.service.UserService;

/**
 * Административное управление пользователями.
 *
 * <pre>
 * | Операция                      | Привилегия       | Роль  |
 * | ----------------------------- | ---------------- | ----- |
 * | POST /api/admin/users/{id}/deactivate | USER_DEACTIVATE | ADMIN |
 * </pre>
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * UC-2: Деактивация пользователя.
     * <p>
     * Сначала пользователь деактивируется в auth DB, затем в business DB
     * закрываются все его открытые заявки.
     */
    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority(T(blps.itmo.security.Privileges).USER_DEACTIVATE)")
    public DeactivateUserResponse deactivateUser(@PathVariable Long id) {
        int closed = userService.deactivateUser(id);
        return DeactivateUserResponse.builder()
                .userId(id)
                .closedClaimsCount(closed)
                .message("User deactivated. Open claims closed: " + closed)
                .build();
    }
}
