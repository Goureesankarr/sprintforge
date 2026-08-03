package dev.sreedaya.sprintforge.task;

import dev.sreedaya.sprintforge.audit.AuditEvent;
import dev.sreedaya.sprintforge.audit.AuditEventRepository;
import dev.sreedaya.sprintforge.common.ApiExceptionHandler.NotFoundException;
import dev.sreedaya.sprintforge.project.Project;
import dev.sreedaya.sprintforge.project.ProjectRepository;
import dev.sreedaya.sprintforge.user.User;
import dev.sreedaya.sprintforge.user.UserRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/work-items")
public class WorkItemController {
    private final WorkItemRepository workItems;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final AuditEventRepository auditEvents;

    public WorkItemController(
            WorkItemRepository workItems,
            ProjectRepository projects,
            UserRepository users,
            AuditEventRepository auditEvents) {
        this.workItems = workItems;
        this.projects = projects;
        this.users = users;
        this.auditEvents = auditEvents;
    }

    public record WorkItemRequest(
            @NotBlank @Size(max = 240) String title,
            @Size(max = 4000) String description,
            WorkItem.Status status,
            @NotNull WorkItem.Priority priority,
            UUID assigneeId,
            LocalDate dueDate) {}

    public record WorkItemView(
            UUID id,
            UUID projectId,
            String title,
            String description,
            String status,
            String priority,
            UUID assigneeId,
            LocalDate dueDate,
            Instant createdAt,
            Instant updatedAt,
            long version) {
        static WorkItemView from(WorkItem workItem) {
            UUID assigneeId = workItem.getAssignee() == null
                    ? null
                    : workItem.getAssignee().getId();
            return new WorkItemView(
                    workItem.getId(),
                    workItem.getProject().getId(),
                    workItem.getTitle(),
                    workItem.getDescription(),
                    workItem.getStatus().name(),
                    workItem.getPriority().name(),
                    assigneeId,
                    workItem.getDueDate(),
                    workItem.getCreatedAt(),
                    workItem.getUpdatedAt(),
                    workItem.getVersion());
        }
    }

    public record BoardSummary(Map<String, Long> counts, long total) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    WorkItemView create(
            @PathVariable UUID projectId,
            @Valid @RequestBody WorkItemRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Project project = accessibleProject(projectId, jwt);
        Instant now = Instant.now();
        WorkItem workItem = new WorkItem();
        workItem.setId(UUID.randomUUID());
        workItem.setProject(project);
        applyRequest(workItem, request, projectId);
        workItem.setCreatedAt(now);
        workItem.setUpdatedAt(now);

        workItems.save(workItem);
        recordAuditEvent(project, currentUser(jwt), "WORK_ITEM_CREATED", workItem.getId());
        return WorkItemView.from(workItem);
    }

    @GetMapping
    @Transactional
    Page<WorkItemView> list(
            @PathVariable UUID projectId,
            @RequestParam(required = false) WorkItem.Status status,
            @RequestParam(required = false) WorkItem.Priority priority,
            @RequestParam(required = false) String q,
            @PageableDefault(
                    size = 20,
                    sort = "updatedAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        accessibleProject(projectId, jwt);
        String query = q == null || q.isBlank() ? null : q.trim();
        return workItems.search(projectId, status, priority, query, pageable)
                .map(WorkItemView::from);
    }

    @PutMapping("/{id}")
    @Transactional
    WorkItemView update(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            @Valid @RequestBody WorkItemRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Project project = accessibleProject(projectId, jwt);
        WorkItem workItem = findWorkItem(id, projectId);
        applyRequest(workItem, request, projectId);
        workItem.setUpdatedAt(Instant.now());
        recordAuditEvent(project, currentUser(jwt), "WORK_ITEM_UPDATED", workItem.getId());
        return WorkItemView.from(workItem);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void delete(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        Project project = accessibleProject(projectId, jwt);
        WorkItem workItem = findWorkItem(id, projectId);
        workItems.delete(workItem);
        recordAuditEvent(project, currentUser(jwt), "WORK_ITEM_DELETED", workItem.getId());
    }

    @GetMapping("/summary")
    @Transactional
    BoardSummary summary(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal Jwt jwt) {
        accessibleProject(projectId, jwt);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (WorkItem.Status status : WorkItem.Status.values()) {
            counts.put(status.name(), 0L);
        }
        workItems.countByStatus(projectId).forEach(row ->
                counts.put(((WorkItem.Status) row[0]).name(), (Long) row[1]));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new BoardSummary(counts, total);
    }

    private void applyRequest(
            WorkItem workItem, WorkItemRequest request, UUID projectId) {
        workItem.setTitle(request.title().trim());
        workItem.setDescription(request.description());
        workItem.setStatus(request.status() == null
                ? WorkItem.Status.BACKLOG
                : request.status());
        workItem.setPriority(request.priority());
        workItem.setAssignee(resolveAssignee(projectId, request.assigneeId()));
        workItem.setDueDate(request.dueDate());
    }

    private User resolveAssignee(UUID projectId, UUID assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        if (!projects.canAccess(projectId, assigneeId)) {
            throw new NotFoundException("Assignee is not a project member");
        }
        return users.findById(assigneeId)
                .orElseThrow(() -> new NotFoundException("Assignee not found"));
    }

    private Project accessibleProject(UUID projectId, Jwt jwt) {
        if (!projects.canAccess(projectId, userId(jwt))) {
            throw new NotFoundException("Project not found");
        }
        return projects.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
    }

    private WorkItem findWorkItem(UUID id, UUID projectId) {
        WorkItem workItem = workItems.findById(id)
                .orElseThrow(() -> new NotFoundException("Work item not found"));
        if (!workItem.getProject().getId().equals(projectId)) {
            throw new NotFoundException("Work item not found");
        }
        return workItem;
    }

    private User currentUser(Jwt jwt) {
        return users.findById(userId(jwt))
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private void recordAuditEvent(
            Project project, User actor, String action, UUID entityId) {
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setProject(project);
        event.setActor(actor);
        event.setAction(action);
        event.setEntityType("WORK_ITEM");
        event.setEntityId(entityId);
        event.setOccurredAt(Instant.now());
        auditEvents.save(event);
    }
}
