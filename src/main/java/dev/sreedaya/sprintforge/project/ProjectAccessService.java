package dev.sreedaya.sprintforge.project;

import dev.sreedaya.sprintforge.common.ApiExceptionHandler.NotFoundException;
import dev.sreedaya.sprintforge.user.User;
import dev.sreedaya.sprintforge.user.UserRepository;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccessService {
    private final ProjectRepository projects;
    private final UserRepository users;

    public ProjectAccessService(ProjectRepository projects, UserRepository users) {
        this.projects = projects;
        this.users = users;
    }

    public Project requireMember(UUID projectId, Jwt jwt) {
        UUID userId = userId(jwt);
        if (!projects.canAccess(projectId, userId)) {
            throw new NotFoundException("Project not found");
        }
        return projects.findActiveById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
    }

    public Project requireOwner(UUID projectId, Jwt jwt) {
        Project project = requireMember(projectId, jwt);
        if (!project.getOwner().getId().equals(userId(jwt))) {
            throw new AccessDeniedException("Only the project owner can perform this action");
        }
        return project;
    }

    public User currentUser(Jwt jwt) {
        return users.findById(userId(jwt))
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    public UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
