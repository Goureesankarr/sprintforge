package dev.sreedaya.sprintforge.project;

import dev.sreedaya.sprintforge.common.ApiExceptionHandler.NotFoundException;
import dev.sreedaya.sprintforge.user.User;
import dev.sreedaya.sprintforge.user.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {
    @Mock
    ProjectRepository projects;

    @Mock
    UserRepository users;

    @Mock
    Jwt jwt;

    @InjectMocks
    ProjectAccessService access;

    @Test
    void returnsProjectForMember() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);
        when(jwt.getSubject()).thenReturn(userId.toString());
        when(projects.canAccess(projectId, userId)).thenReturn(true);
        when(projects.findActiveById(projectId)).thenReturn(Optional.of(project));

        assertEquals(project, access.requireMember(projectId, jwt));
    }

    @Test
    void hidesProjectFromNonMember() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(jwt.getSubject()).thenReturn(userId.toString());
        when(projects.canAccess(projectId, userId)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> access.requireMember(projectId, jwt));
    }

    @Test
    void deniesOwnerOperationToRegularMember() {
        UUID memberId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User owner = new User();
        owner.setId(UUID.randomUUID());
        Project project = new Project();
        project.setId(projectId);
        project.setOwner(owner);
        when(jwt.getSubject()).thenReturn(memberId.toString());
        when(projects.canAccess(projectId, memberId)).thenReturn(true);
        when(projects.findActiveById(projectId)).thenReturn(Optional.of(project));

        assertThrows(AccessDeniedException.class, () -> access.requireOwner(projectId, jwt));
    }
}
