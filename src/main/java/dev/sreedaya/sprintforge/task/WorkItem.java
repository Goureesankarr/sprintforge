package dev.sreedaya.sprintforge.task;

import dev.sreedaya.sprintforge.project.Project;
import dev.sreedaya.sprintforge.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name = "work_items")
@Getter @Setter @NoArgsConstructor
public class WorkItem {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id") private Project project;
    @Column(nullable = false) private String title;
    private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Priority priority;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assignee_id") private User assignee;
    @Column(name = "due_date") private LocalDate dueDate;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    public enum Status { BACKLOG, TODO, IN_PROGRESS, IN_REVIEW, DONE }
    public enum Priority { LOW, MEDIUM, HIGH, CRITICAL }
}
