package dev.sreedaya.sprintforge.user;

import dev.sreedaya.sprintforge.common.ApiExceptionHandler.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final UserRepository users;

    public AdminUserController(UserRepository users) {
        this.users = users;
    }

    public record ChangeRoleRequest(@NotNull User.Role role) {}

    public record UserRoleView(UUID id, String email, String role) {}

    @PatchMapping("/{userId}/role")
    UserRoleView changeRole(
            @PathVariable UUID userId, @Valid @RequestBody ChangeRoleRequest request) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.setRole(request.role());
        users.save(user);
        return new UserRoleView(user.getId(), user.getEmail(), user.getRole().name());
    }
}
