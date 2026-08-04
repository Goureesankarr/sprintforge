package dev.sreedaya.sprintforge.project;

import dev.sreedaya.sprintforge.audit.AuditEvent;
import dev.sreedaya.sprintforge.audit.AuditEventRepository;
import dev.sreedaya.sprintforge.common.ApiExceptionHandler.ConflictException;
import dev.sreedaya.sprintforge.common.ApiExceptionHandler.NotFoundException;
import dev.sreedaya.sprintforge.notification.EmailNotificationService;
import dev.sreedaya.sprintforge.realtime.ProjectEventPublisher;
import dev.sreedaya.sprintforge.user.User;
import dev.sreedaya.sprintforge.user.UserRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectRepository projects;
    private final UserRepository users;
    private final AuditEventRepository auditEvents;
    private final ProjectAccessService access;
    private final EmailNotificationService notifications;
    private final ProjectEventPublisher events;

    public ProjectController(
            ProjectRepository projects,
            UserRepository users,
            AuditEventRepository auditEvents,
            ProjectAccessService access,
            EmailNotificationService notifications,
            ProjectEventPublisher events) {
        this.projects = projects;
        this.users = users;
        this.auditEvents = auditEvents;
        this.access = access;
        this.notifications = notifications;
        this.events = events;
    }

    public record CreateProject(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9]{1,11}") String key,
            @Size(max = 2000) String description) {}

    public record ProjectView(
            UUID id,
            String name,
            String key,
            String description,
            String status,
            UUID ownerId,
            int memberCount,
            Instant createdAt,
            Instant updatedAt,
            long version) {
        static ProjectView from(Project project) {
            return new ProjectView(
                    project.getId(),
                    project.getName(),
                    project.getKey(),
                    project.getDescription(),
                    project.getStatus().name(),
                    project.getOwner().getId(),
                    project.getMembers().size(),
                    project.getCreatedAt(),
                    project.getUpdatedAt(),
                    project.getVersion());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    ProjectView create(
            @Valid @RequestBody CreateProject request,
            @AuthenticationPrincipal Jwt jwt) {
        String projectKey = request.key().toUpperCase();
        if (projects.existsByKeyIgnoreCase(projectKey)) {
            throw new ConflictException("Project key already exists");
        }

        User owner = access.currentUser(jwt);
        Instant now = Instant.now();
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName(request.name().trim());
        project.setKey(projectKey);
        project.setDescription(request.description());
        project.setOwner(owner);
        project.getMembers().add(owner);
        project.setStatus(Project.Status.ACTIVE);
        project.setCreatedAt(now);
        project.setUpdatedAt(now);

        projects.save(project);
        recordAuditEvent(project, owner, "PROJECT_CREATED", project.getId());
        log.info("Project created: projectId={}, ownerId={}", project.getId(), owner.getId());
        return ProjectView.from(project);
    }

    @GetMapping
    @Transactional
    List<ProjectView> list(@AuthenticationPrincipal Jwt jwt) {
        return projects.findAccessible(access.userId(jwt)).stream()
                .map(ProjectView::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional
    ProjectView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return ProjectView.from(access.requireMember(id, jwt));
    }

    @PostMapping("/{id}/members/{email}")
    @Transactional
    ProjectView addMember(
            @PathVariable UUID id,
            @PathVariable String email,
            @AuthenticationPrincipal Jwt jwt) {
        User actor = access.currentUser(jwt);
        Project project = access.requireOwner(id, jwt);

        User member = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
        project.getMembers().add(member);
        project.setUpdatedAt(Instant.now());
        recordAuditEvent(project, actor, "MEMBER_ADDED", member.getId());
        notifications.projectInvitation(member.getEmail(), project.getName(), project.getId());
        events.publish(project.getId(), "MEMBER_ADDED", "USER", member.getId());
        log.info("Project member added: projectId={}, memberId={}", id, member.getId());
        return ProjectView.from(project);
    }

    @PatchMapping("/{id}/archive")
    @Transactional
    ProjectView archive(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        User actor = access.currentUser(jwt);
        Project project = access.requireOwner(id, jwt);

        project.setStatus(Project.Status.ARCHIVED);
        project.setUpdatedAt(Instant.now());
        recordAuditEvent(project, actor, "PROJECT_ARCHIVED", project.getId());
        events.publish(project.getId(), "PROJECT_ARCHIVED", "PROJECT", project.getId());
        log.info("Project archived: projectId={}, actorId={}", id, actor.getId());
        return ProjectView.from(project);
    }

    private void recordAuditEvent(
            Project project, User actor, String action, UUID entityId) {
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setProject(project);
        event.setActor(actor);
        event.setAction(action);
        event.setEntityType("PROJECT");
        event.setEntityId(entityId);
        event.setOccurredAt(Instant.now());
        auditEvents.save(event);
    }
}
