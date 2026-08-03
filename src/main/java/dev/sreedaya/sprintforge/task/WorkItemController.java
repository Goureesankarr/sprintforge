package dev.sreedaya.sprintforge.task;

import dev.sreedaya.sprintforge.audit.*;
import dev.sreedaya.sprintforge.common.ApiExceptionHandler.NotFoundException;
import dev.sreedaya.sprintforge.project.*;
import dev.sreedaya.sprintforge.user.*;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/projects/{projectId}/work-items")
public class WorkItemController {
    private final WorkItemRepository items; private final ProjectRepository projects; private final UserRepository users; private final AuditEventRepository audit;
    public WorkItemController(WorkItemRepository items,ProjectRepository projects,UserRepository users,AuditEventRepository audit){this.items=items;this.projects=projects;this.users=users;this.audit=audit;}

    public record WorkItemRequest(@NotBlank @Size(max=240) String title,@Size(max=4000) String description,
                                  WorkItem.Status status,@NotNull WorkItem.Priority priority,UUID assigneeId,LocalDate dueDate){}
    public record WorkItemView(UUID id,UUID projectId,String title,String description,String status,String priority,UUID assigneeId,
                               LocalDate dueDate,Instant createdAt,Instant updatedAt,long version){
        static WorkItemView of(WorkItem w){return new WorkItemView(w.getId(),w.getProject().getId(),w.getTitle(),w.getDescription(),w.getStatus().name(),w.getPriority().name(),w.getAssignee()==null?null:w.getAssignee().getId(),w.getDueDate(),w.getCreatedAt(),w.getUpdatedAt(),w.getVersion());}
    }
    public record BoardSummary(Map<String,Long> counts,long total){}

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    WorkItemView create(@PathVariable UUID projectId,@Valid @RequestBody WorkItemRequest request,@AuthenticationPrincipal Jwt jwt){
        var project=access(projectId,jwt);var now=Instant.now();var w=new WorkItem();w.setId(UUID.randomUUID());w.setProject(project);apply(w,request);w.setCreatedAt(now);w.setUpdatedAt(now);items.save(w);record(project,user(jwt),"WORK_ITEM_CREATED",w.getId());return WorkItemView.of(w);
    }
    @GetMapping @Transactional
    Page<WorkItemView> list(@PathVariable UUID projectId,@RequestParam(required=false) WorkItem.Status status,
                            @RequestParam(required=false) WorkItem.Priority priority,@RequestParam(required=false) String q,
                            @PageableDefault(size=20,sort="updatedAt",direction=Sort.Direction.DESC) Pageable pageable,@AuthenticationPrincipal Jwt jwt){
        access(projectId,jwt);return items.search(projectId,status,priority,(q==null||q.isBlank())?null:q.trim(),pageable).map(WorkItemView::of);
    }
    @PutMapping("/{id}") @Transactional
    WorkItemView update(@PathVariable UUID projectId,@PathVariable UUID id,@Valid @RequestBody WorkItemRequest request,@AuthenticationPrincipal Jwt jwt){
        var project=access(projectId,jwt);var w=find(id,projectId);apply(w,request);w.setUpdatedAt(Instant.now());record(project,user(jwt),"WORK_ITEM_UPDATED",w.getId());return WorkItemView.of(w);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    void delete(@PathVariable UUID projectId,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){var project=access(projectId,jwt);var w=find(id,projectId);items.delete(w);record(project,user(jwt),"WORK_ITEM_DELETED",w.getId());}
    @GetMapping("/summary") @Transactional
    BoardSummary summary(@PathVariable UUID projectId,@AuthenticationPrincipal Jwt jwt){access(projectId,jwt);var counts=new LinkedHashMap<String,Long>();for(var s:WorkItem.Status.values())counts.put(s.name(),0L);items.countByStatus(projectId).forEach(row->counts.put(((WorkItem.Status)row[0]).name(),(Long)row[1]));return new BoardSummary(counts,counts.values().stream().mapToLong(Long::longValue).sum());}

    private void apply(WorkItem w,WorkItemRequest r){w.setTitle(r.title().trim());w.setDescription(r.description());w.setStatus(r.status()==null?WorkItem.Status.BACKLOG:r.status());w.setPriority(r.priority());w.setAssignee(r.assigneeId()==null?null:users.findById(r.assigneeId()).orElseThrow(()->new NotFoundException("Assignee not found")));w.setDueDate(r.dueDate());}
    private Project access(UUID id,Jwt jwt){if(!projects.canAccess(id,userId(jwt)))throw new NotFoundException("Project not found");return projects.findById(id).orElseThrow(()->new NotFoundException("Project not found"));}
    private WorkItem find(UUID id,UUID projectId){var w=items.findById(id).orElseThrow(()->new NotFoundException("Work item not found"));if(!w.getProject().getId().equals(projectId))throw new NotFoundException("Work item not found");return w;}
    private User user(Jwt jwt){return users.findById(userId(jwt)).orElseThrow(()->new NotFoundException("User not found"));}
    private UUID userId(Jwt jwt){return UUID.fromString(jwt.getSubject());}
    private void record(Project p,User actor,String action,UUID id){var e=new AuditEvent();e.setId(UUID.randomUUID());e.setProject(p);e.setActor(actor);e.setAction(action);e.setEntityType("WORK_ITEM");e.setEntityId(id);e.setOccurredAt(Instant.now());audit.save(e);}
}
