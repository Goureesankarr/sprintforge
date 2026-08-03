package dev.sreedaya.sprintforge.project;

import dev.sreedaya.sprintforge.audit.*;
import dev.sreedaya.sprintforge.common.ApiExceptionHandler.*;
import dev.sreedaya.sprintforge.user.*;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController @RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectRepository projects; private final UserRepository users; private final AuditEventRepository audit;
    public ProjectController(ProjectRepository projects, UserRepository users, AuditEventRepository audit) { this.projects=projects; this.users=users; this.audit=audit; }

    public record CreateProject(@NotBlank @Size(max=160) String name, @NotBlank @Pattern(regexp="[A-Za-z][A-Za-z0-9]{1,11}") String key,
                                @Size(max=2000) String description) {}
    public record ProjectView(UUID id, String name, String key, String description, String status, UUID ownerId, int memberCount, Instant createdAt, Instant updatedAt, long version) {
        static ProjectView of(Project p) { return new ProjectView(p.getId(),p.getName(),p.getKey(),p.getDescription(),p.getStatus().name(),p.getOwner().getId(),p.getMembers().size(),p.getCreatedAt(),p.getUpdatedAt(),p.getVersion()); }
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    ProjectView create(@Valid @RequestBody CreateProject request, @AuthenticationPrincipal Jwt jwt) {
        var key=request.key().toUpperCase(); if(projects.existsByKeyIgnoreCase(key)) throw new ConflictException("Project key already exists");
        var owner=user(jwt); var now=Instant.now(); var p=new Project(); p.setId(UUID.randomUUID()); p.setName(request.name().trim()); p.setKey(key);
        p.setDescription(request.description()); p.setOwner(owner); p.getMembers().add(owner); p.setStatus(Project.Status.ACTIVE); p.setCreatedAt(now); p.setUpdatedAt(now);
        projects.save(p); record(p,owner,"PROJECT_CREATED",p.getId()); return ProjectView.of(p);
    }
    @GetMapping @Transactional
    List<ProjectView> list(@AuthenticationPrincipal Jwt jwt) { return projects.findAccessible(userId(jwt)).stream().map(ProjectView::of).toList(); }
    @GetMapping("/{id}") @Transactional
    ProjectView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) { requireAccess(id,userId(jwt)); return ProjectView.of(find(id)); }
    @PostMapping("/{id}/members/{email}") @Transactional
    ProjectView addMember(@PathVariable UUID id, @PathVariable String email, @AuthenticationPrincipal Jwt jwt) {
        var p=find(id); var actor=user(jwt); if(!p.getOwner().getId().equals(actor.getId())) throw new org.springframework.security.access.AccessDeniedException("Owner only");
        var member=users.findByEmailIgnoreCase(email).orElseThrow(() -> new NotFoundException("User not found")); p.getMembers().add(member); p.setUpdatedAt(Instant.now()); record(p,actor,"MEMBER_ADDED",member.getId()); return ProjectView.of(p);
    }
    @PatchMapping("/{id}/archive") @Transactional
    ProjectView archive(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) { var p=find(id); var actor=user(jwt); if(!p.getOwner().getId().equals(actor.getId())) throw new org.springframework.security.access.AccessDeniedException("Owner only"); p.setStatus(Project.Status.ARCHIVED);p.setUpdatedAt(Instant.now());record(p,actor,"PROJECT_ARCHIVED",p.getId());return ProjectView.of(p); }

    public void requireAccess(UUID projectId,UUID userId){if(!projects.canAccess(projectId,userId))throw new NotFoundException("Project not found");}
    public Project find(UUID id){return projects.findById(id).orElseThrow(()->new NotFoundException("Project not found"));}
    private User user(Jwt jwt){return users.findById(userId(jwt)).orElseThrow(()->new NotFoundException("User not found"));}
    private UUID userId(Jwt jwt){return UUID.fromString(jwt.getSubject());}
    private void record(Project p,User actor,String action,UUID entityId){var e=new AuditEvent();e.setId(UUID.randomUUID());e.setProject(p);e.setActor(actor);e.setAction(action);e.setEntityType("PROJECT");e.setEntityId(entityId);e.setOccurredAt(Instant.now());audit.save(e);}
}
