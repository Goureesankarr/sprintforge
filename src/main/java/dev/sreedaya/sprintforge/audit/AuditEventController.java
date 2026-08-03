package dev.sreedaya.sprintforge.audit;

import dev.sreedaya.sprintforge.common.ApiExceptionHandler.NotFoundException;
import dev.sreedaya.sprintforge.project.ProjectRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/audit-events")
public class AuditEventController {
    private final AuditEventRepository auditEvents;
    private final ProjectRepository projects;

    public AuditEventController(
            AuditEventRepository auditEvents, ProjectRepository projects) {
        this.auditEvents = auditEvents;
        this.projects = projects;
    }

    public record AuditEventView(
            UUID id,
            String action,
            String entityType,
            UUID entityId,
            UUID actorId,
            Instant occurredAt) {
        static AuditEventView from(AuditEvent event) {
            return new AuditEventView(
                    event.getId(),
                    event.getAction(),
                    event.getEntityType(),
                    event.getEntityId(),
                    event.getActor().getId(),
                    event.getOccurredAt());
        }
    }

    @GetMapping
    @Transactional
    Page<AuditEventView> list(
            @PathVariable UUID projectId,
            @PageableDefault(size = 30) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        if (!projects.canAccess(projectId, userId)) {
            throw new NotFoundException("Project not found");
        }
        return auditEvents.findByProjectIdOrderByOccurredAtDesc(projectId, pageable)
                .map(AuditEventView::from);
    }
}
