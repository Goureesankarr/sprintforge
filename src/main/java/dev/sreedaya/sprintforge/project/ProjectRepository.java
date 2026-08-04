package dev.sreedaya.sprintforge.project;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    boolean existsByKeyIgnoreCase(String key);

    @Query("""
            select distinct p from Project p
            left join p.members m
            where p.deletedAt is null
              and (p.owner.id = :userId or m.id = :userId)
            order by p.updatedAt desc
            """)
    List<Project> findAccessible(@Param("userId") UUID userId);

    @Query("""
            select count(p) > 0 from Project p
            left join p.members m
            where p.id = :projectId
              and p.deletedAt is null
              and (p.owner.id = :userId or m.id = :userId)
            """)
    boolean canAccess(
            @Param("projectId") UUID projectId,
            @Param("userId") UUID userId);

    @Query("select p from Project p where p.id = :id and p.deletedAt is null")
    java.util.Optional<Project> findActiveById(@Param("id") UUID id);
}
