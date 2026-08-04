package dev.sreedaya.sprintforge.task;

import dev.sreedaya.sprintforge.audit.AuditEvent;
import dev.sreedaya.sprintforge.audit.AuditEventRepository;
import dev.sreedaya.sprintforge.common.ApiExceptionHandler.NotFoundException;
import dev.sreedaya.sprintforge.project.Project;
import dev.sreedaya.sprintforge.project.ProjectAccessService;
import dev.sreedaya.sprintforge.project.ProjectRepository;
import dev.sreedaya.sprintforge.realtime.ProjectEventPublisher;
import dev.sreedaya.sprintforge.sprint.Sprint;
import dev.sreedaya.sprintforge.sprint.SprintService;
import dev.sreedaya.sprintforge.user.User;
import dev.sreedaya.sprintforge.user.UserRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
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
    private static final Logger log = LoggerFactory.getLogger(WorkItemController.class);

    private final WorkItemRepository workItems;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final AuditEventRepository auditEvents;
    private final SprintService sprintService;
    private final WorkItemWorkflow workflow;
    private final WorkItemSummaryService summaries;
    private final ProjectAccessService access;
    private final ProjectEventPublisher events;

    public WorkItemController(
            WorkItemRepository workItems,
            ProjectRepository projects,
            UserRepository users,
            AuditEventRepository auditEvents,
            SprintService sprintService,
            WorkItemWorkflow workflow,
            WorkItemSummaryService summaries,
            ProjectAccessService access,
            ProjectEventPublisher events) {
        this.workItems = workItems;
        this.projects = projects;
        this.users = users;
        this.auditEvents = auditEvents;
        this.sprintService = sprintService;
        this.workflow = workflow;
        this.summaries = summaries;
        this.access = access;
        this.events = events;
    }

    public record WorkItemRequest(
            @NotBlank @Size(max = 240) String title,
            @Size(max = 4000) String description,
            WorkItem.Status status,
            @NotNull WorkItem.Priority priority,
            UUID assigneeId,
            UUID sprintId,
            LocalDate dueDate) {}

    public record WorkItemView(
            UUID id,
            UUID projectId,
            UUID sprintId,
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
                    workItem.getSprint() == null ? null : workItem.getSprint().getId(),
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

    public record BoardSummary(Map<String, Long> counts, long total)
            implements java.io.Serializable {
        public BoardSummary {
            counts = Map.copyOf(counts);
        }

        @Override
        public Map<String, Long> counts() {
            return Map.copyOf(counts);
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @CacheEvict(cacheNames = "boardSummaries", key = "#projectId")
    WorkItemView create(
            @PathVariable UUID projectId,
            @Valid @RequestBody WorkItemRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Project project = access.requireMember(projectId, jwt);
        Instant now = Instant.now();
        WorkItem workItem = new WorkItem();
        workItem.setId(UUID.randomUUID());
        workItem.setProject(project);
        applyRequest(workItem, request, projectId);
        workItem.setCreatedAt(now);
        workItem.setUpdatedAt(now);

        workItems.save(workItem);
        recordAuditEvent(project, access.currentUser(jwt), "WORK_ITEM_CREATED", workItem.getId());
        events.publish(projectId, "WORK_ITEM_CREATED", "WORK_ITEM", workItem.getId());
        log.info("Work item created: workItemId={}, projectId={}", workItem.getId(), projectId);
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
        access.requireMember(projectId, jwt);
        String query = q == null || q.isBlank() ? null : q.trim();
        return workItems.search(projectId, status, priority, query, pageable)
                .map(WorkItemView::from);
    }

    @PutMapping("/{id}")
    @Transactional
    @CacheEvict(cacheNames = "boardSummaries", key = "#projectId")
    WorkItemView update(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            @Valid @RequestBody WorkItemRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Project project = access.requireMember(projectId, jwt);
        WorkItem workItem = findWorkItem(id, projectId);
        workflow.validateTransition(workItem.getStatus(), requestedStatus(request));
        WorkItem.Status previousStatus = workItem.getStatus();
        applyRequest(workItem, request, projectId);
        workItem.setUpdatedAt(Instant.now());
        recordAuditEvent(project, access.currentUser(jwt), "WORK_ITEM_UPDATED", workItem.getId());
        events.publish(projectId, "WORK_ITEM_UPDATED", "WORK_ITEM", workItem.getId());
        if (previousStatus != workItem.getStatus()) {
            log.info(
                    "Work item status changed: workItemId={}, from={}, to={}",
                    id,
                    previousStatus,
                    workItem.getStatus());
        }
        return WorkItemView.from(workItem);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    @CacheEvict(cacheNames = "boardSummaries", key = "#projectId")
    void delete(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        Project project = access.requireMember(projectId, jwt);
        WorkItem workItem = findWorkItem(id, projectId);
        workItem.setDeletedAt(Instant.now());
        workItem.setUpdatedAt(Instant.now());
        recordAuditEvent(project, access.currentUser(jwt), "WORK_ITEM_DELETED", workItem.getId());
        events.publish(projectId, "WORK_ITEM_DELETED", "WORK_ITEM", workItem.getId());
    }

    @GetMapping("/summary")
    @Transactional
    BoardSummary summary(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal Jwt jwt) {
        access.requireMember(projectId, jwt);
        return summaries.summarize(projectId);
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
        workItem.setSprint(resolveSprint(projectId, request.sprintId()));
        workItem.setDueDate(request.dueDate());
    }

    private WorkItem.Status requestedStatus(WorkItemRequest request) {
        return request.status() == null ? WorkItem.Status.BACKLOG : request.status();
    }

    private Sprint resolveSprint(UUID projectId, UUID sprintId) {
        return sprintId == null ? null : sprintService.find(projectId, sprintId);
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

    private WorkItem findWorkItem(UUID id, UUID projectId) {
        WorkItem workItem = workItems.findActiveByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new NotFoundException("Work item not found"));
        return workItem;
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
