package dev.sreedaya.sprintforge.project;

import dev.sreedaya.sprintforge.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.*;

@Entity @Table(name = "projects")
@Getter @Setter @NoArgsConstructor
public class Project {
    @Id private UUID id;
    @Column(nullable = false) private String name;
    @Column(name = "project_key", nullable = false, unique = true) private String key;
    private String description;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id") private User owner;
    @ManyToMany
    @JoinTable(name = "project_members", joinColumns = @JoinColumn(name = "project_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> members = new HashSet<>();
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    public enum Status { ACTIVE, ARCHIVED }
}
