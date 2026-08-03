package dev.sreedaya.sprintforge.project;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    boolean existsByKeyIgnoreCase(String key);
    @Query("select distinct p from Project p left join p.members m where p.owner.id = :userId or m.id = :userId order by p.updatedAt desc")
    List<Project> findAccessible(@Param("userId") UUID userId);
    @Query("select count(p) > 0 from Project p left join p.members m where p.id = :projectId and (p.owner.id = :userId or m.id = :userId)")
    boolean canAccess(@Param("projectId") UUID projectId, @Param("userId") UUID userId);
}
