package dev.sreedaya.sprintforge.comment;

import dev.sreedaya.sprintforge.audit.AuditEvent;
import dev.sreedaya.sprintforge.audit.AuditEventRepository;
import dev.sreedaya.sprintforge.common.ApiExceptionHandler.NotFoundException;
import dev.sreedaya.sprintforge.project.Project;
import dev.sreedaya.sprintforge.project.ProjectAccessService;
import dev.sreedaya.sprintforge.realtime.ProjectEventPublisher;
import dev.sreedaya.sprintforge.task.WorkItem;
import dev.sreedaya.sprintforge.task.WorkItemRepository;
import dev.sreedaya.sprintforge.user.User;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/work-items/{workItemId}/comments")
public class WorkItemCommentController {
    private final WorkItemCommentRepository comments;
    private final WorkItemRepository workItems;
    private final AuditEventRepository auditEvents;
    private final ProjectAccessService access;
    private final ProjectEventPublisher events;

    public WorkItemCommentController(
            WorkItemCommentRepository comments,
            WorkItemRepository workItems,
            AuditEventRepository auditEvents,
            ProjectAccessService access,
            ProjectEventPublisher events) {
        this.comments = comments;
        this.workItems = workItems;
        this.auditEvents = auditEvents;
        this.access = access;
        this.events = events;
    }

    public record CommentRequest(@NotBlank @Size(max = 4000) String body) {}

    public record CommentView(
            UUID id,
            UUID workItemId,
            UUID authorId,
            String authorName,
            String body,
            Instant createdAt,
            Instant updatedAt,
            long version) {
        static CommentView from(WorkItemComment comment) {
            return new CommentView(
                    comment.getId(),
                    comment.getWorkItem().getId(),
                    comment.getAuthor().getId(),
                    comment.getAuthor().getDisplayName(),
                    comment.getBody(),
                    comment.getCreatedAt(),
                    comment.getUpdatedAt(),
                    comment.getVersion());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    CommentView create(
            @PathVariable UUID projectId,
            @PathVariable UUID workItemId,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Project project = access.requireMember(projectId, jwt);
        WorkItem workItem = requireWorkItem(projectId, workItemId);
        User author = access.currentUser(jwt);
        Instant now = Instant.now();

        WorkItemComment comment = new WorkItemComment();
        comment.setId(UUID.randomUUID());
        comment.setWorkItem(workItem);
        comment.setAuthor(author);
        comment.setBody(request.body().trim());
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);
        comments.save(comment);

        recordAuditEvent(project, author, "COMMENT_CREATED", comment.getId());
        events.publish(projectId, "COMMENT_CREATED", "COMMENT", comment.getId());
        return CommentView.from(comment);
    }

    @GetMapping
    @Transactional
    Page<CommentView> list(
            @PathVariable UUID projectId,
            @PathVariable UUID workItemId,
            @PageableDefault(
                    size = 30,
                    sort = "createdAt",
                    direction = Sort.Direction.ASC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        access.requireMember(projectId, jwt);
        requireWorkItem(projectId, workItemId);
        return comments.findActiveTimeline(projectId, workItemId, pageable)
                .map(CommentView::from);
    }

    @PatchMapping("/{commentId}")
    @Transactional
    CommentView update(
            @PathVariable UUID projectId,
            @PathVariable UUID workItemId,
            @PathVariable UUID commentId,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Project project = access.requireMember(projectId, jwt);
        WorkItemComment comment = requireComment(projectId, workItemId, commentId);
        User actor = access.currentUser(jwt);
        requireAuthor(comment, actor);

        comment.setBody(request.body().trim());
        comment.setUpdatedAt(Instant.now());
        recordAuditEvent(project, actor, "COMMENT_UPDATED", comment.getId());
        events.publish(projectId, "COMMENT_UPDATED", "COMMENT", comment.getId());
        return CommentView.from(comment);
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void delete(
            @PathVariable UUID projectId,
            @PathVariable UUID workItemId,
            @PathVariable UUID commentId,
            @AuthenticationPrincipal Jwt jwt) {
        Project project = access.requireMember(projectId, jwt);
        WorkItemComment comment = requireComment(projectId, workItemId, commentId);
        User actor = access.currentUser(jwt);
        requireAuthorOrOwner(project, comment, actor);

        comment.setDeletedAt(Instant.now());
        comment.setUpdatedAt(Instant.now());
        recordAuditEvent(project, actor, "COMMENT_DELETED", comment.getId());
        events.publish(projectId, "COMMENT_DELETED", "COMMENT", comment.getId());
    }

    private WorkItem requireWorkItem(UUID projectId, UUID workItemId) {
        return workItems.findActiveByIdAndProjectId(workItemId, projectId)
                .orElseThrow(() -> new NotFoundException("Work item not found"));
    }

    private WorkItemComment requireComment(
            UUID projectId, UUID workItemId, UUID commentId) {
        return comments.findActive(commentId, projectId, workItemId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));
    }

    private void requireAuthor(WorkItemComment comment, User actor) {
        if (!comment.getAuthor().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Only the comment author can edit it");
        }
    }

    private void requireAuthorOrOwner(
            Project project, WorkItemComment comment, User actor) {
        boolean author = comment.getAuthor().getId().equals(actor.getId());
        boolean owner = project.getOwner().getId().equals(actor.getId());
        if (!author && !owner) {
            throw new AccessDeniedException("Only the comment author or project owner can delete it");
        }
    }

    private void recordAuditEvent(
            Project project, User actor, String action, UUID commentId) {
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setProject(project);
        event.setActor(actor);
        event.setAction(action);
        event.setEntityType("COMMENT");
        event.setEntityId(commentId);
        event.setOccurredAt(Instant.now());
        auditEvents.save(event);
    }
}
