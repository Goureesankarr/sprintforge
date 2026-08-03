package dev.sreedaya.sprintforge.user;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "app_users")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "display_name", nullable = false) private String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Role role;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public enum Role { USER, ADMIN }
}
