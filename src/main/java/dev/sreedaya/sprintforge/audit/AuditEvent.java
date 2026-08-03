package dev.sreedaya.sprintforge.audit;

import dev.sreedaya.sprintforge.project.Project;
import dev.sreedaya.sprintforge.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "audit_events")
@Getter @Setter @NoArgsConstructor
public class AuditEvent {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id") private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "actor_id") private User actor;
    @Column(nullable = false) private String action;
    @Column(name = "entity_type", nullable = false) private String entityType;
    @Column(name = "entity_id", nullable = false) private UUID entityId;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
}
