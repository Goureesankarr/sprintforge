package dev.sreedaya.sprintforge.comment;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkItemCommentRepository extends JpaRepository<WorkItemComment, UUID> {
    @Query("""
            select c from WorkItemComment c
            join fetch c.author
            where c.workItem.id = :workItemId
              and c.workItem.project.id = :projectId
              and c.deletedAt is null
            """)
    Page<WorkItemComment> findActiveTimeline(
            @Param("projectId") UUID projectId,
            @Param("workItemId") UUID workItemId,
            Pageable pageable);

    @Query("""
            select c from WorkItemComment c
            join fetch c.author
            join fetch c.workItem w
            where c.id = :id
              and w.id = :workItemId
              and w.project.id = :projectId
              and c.deletedAt is null
              and w.deletedAt is null
            """)
    Optional<WorkItemComment> findActive(
            @Param("id") UUID id,
            @Param("projectId") UUID projectId,
            @Param("workItemId") UUID workItemId);
}
