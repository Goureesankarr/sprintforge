package dev.sreedaya.sprintforge.sprint;

import dev.sreedaya.sprintforge.audit.AuditEvent;
import dev.sreedaya.sprintforge.audit.AuditEventRepository;
import dev.sreedaya.sprintforge.common.ApiExceptionHandler.ConflictException;
import dev.sreedaya.sprintforge.common.ApiExceptionHandler.BadRequestException;
import dev.sreedaya.sprintforge.common.ApiExceptionHandler.NotFoundException;
import dev.sreedaya.sprintforge.project.Project;
import dev.sreedaya.sprintforge.project.ProjectAccessService;
import dev.sreedaya.sprintforge.realtime.ProjectEventPublisher;
import dev.sreedaya.sprintforge.user.User;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class SprintService {
    private static final Logger log = LoggerFactory.getLogger(SprintService.class);

    private final SprintRepository sprints;
    private final ProjectAccessService access;
    private final AuditEventRepository auditEvents;
    private final Counter sprintCreated;
    private final Counter sprintStatusChanged;
    private final ProjectEventPublisher events;

    public SprintService(
            SprintRepository sprints,
            ProjectAccessService access,
            AuditEventRepository auditEvents,
            MeterRegistry meterRegistry,
            ProjectEventPublisher events) {
        this.sprints = sprints;
        this.access = access;
        this.auditEvents = auditEvents;
        this.sprintCreated = meterRegistry.counter("sprintforge.sprints.created");
        this.sprintStatusChanged = meterRegistry.counter("sprintforge.sprints.status.changed");
        this.events = events;
    }

    @Transactional
    public Sprint create(
            UUID projectId,
            String name,
            String goal,
            LocalDate startDate,
            LocalDate endDate,
            Jwt jwt) {
        Project project = access.requireMember(projectId, jwt);
        if (endDate.isBefore(startDate)) {
            throw new BadRequestException("Sprint end date cannot be before its start date");
        }
        if (sprints.existsByProjectIdAndNameIgnoreCase(projectId, name)) {
            throw new ConflictException("Sprint name already exists in this project");
        }

        Instant now = Instant.now();
        Sprint sprint = new Sprint();
        sprint.setId(UUID.randomUUID());
        sprint.setProject(project);
        sprint.setName(name.trim());
        sprint.setGoal(goal);
        sprint.setStatus(Sprint.Status.PLANNED);
        sprint.setStartDate(startDate);
        sprint.setEndDate(endDate);
        sprint.setCreatedAt(now);
        sprint.setUpdatedAt(now);
        sprints.save(sprint);

        User actor = access.currentUser(jwt);
        recordAuditEvent(project, actor, "SPRINT_CREATED", sprint.getId());
        events.publish(projectId, "SPRINT_CREATED", "SPRINT", sprint.getId());
        sprintCreated.increment();
        log.info("Sprint created: sprintId={}, projectId={}", sprint.getId(), projectId);
        return sprint;
    }

    @Transactional
    public List<Sprint> list(UUID projectId, Jwt jwt) {
        access.requireMember(projectId, jwt);
        return sprints.findByProjectIdOrderByStartDateDesc(projectId);
    }

    @Transactional
    public Sprint changeStatus(
            UUID projectId, UUID sprintId, Sprint.Status requestedStatus, Jwt jwt) {
        Project project = access.requireMember(projectId, jwt);
        Sprint sprint = find(projectId, sprintId);
        validateTransition(sprint.getStatus(), requestedStatus);

        Sprint.Status previousStatus = sprint.getStatus();
        sprint.setStatus(requestedStatus);
        sprint.setUpdatedAt(Instant.now());
        recordAuditEvent(
                project,
                access.currentUser(jwt),
                "SPRINT_STATUS_CHANGED",
                sprint.getId());
        events.publish(projectId, "SPRINT_STATUS_CHANGED", "SPRINT", sprint.getId());
        sprintStatusChanged.increment();
        log.info(
                "Sprint status changed: sprintId={}, from={}, to={}",
                sprintId,
                previousStatus,
                requestedStatus);
        return sprint;
    }

    public Sprint find(UUID projectId, UUID sprintId) {
        return sprints.findByIdAndProjectId(sprintId, projectId)
                .orElseThrow(() -> new NotFoundException("Sprint not found"));
    }

    void validateTransition(Sprint.Status current, Sprint.Status requested) {
        boolean valid = switch (current) {
            case PLANNED -> requested == Sprint.Status.ACTIVE
                    || requested == Sprint.Status.CANCELLED;
            case ACTIVE -> requested == Sprint.Status.COMPLETED
                    || requested == Sprint.Status.CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
        if (!valid) {
            throw new ConflictException(
                    "Sprint cannot move from " + current + " to " + requested);
        }
    }

    private void recordAuditEvent(
            Project project, User actor, String action, UUID entityId) {
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setProject(project);
        event.setActor(actor);
        event.setAction(action);
        event.setEntityType("SPRINT");
        event.setEntityId(entityId);
        event.setOccurredAt(Instant.now());
        auditEvents.save(event);
    }
}
