package dev.sreedaya.sprintforge.task;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface WorkItemRepository extends JpaRepository<WorkItem, UUID> {
    @Query("select w from WorkItem w where w.project.id = :projectId " +
           "and (:status is null or w.status = :status) and (:priority is null or w.priority = :priority) " +
           "and (:query is null or lower(w.title) like lower(concat('%', :query, '%'))) ")
    Page<WorkItem> search(@Param("projectId") UUID projectId, @Param("status") WorkItem.Status status,
                          @Param("priority") WorkItem.Priority priority, @Param("query") String query, Pageable pageable);

    @Query("select w.status, count(w) from WorkItem w where w.project.id = :projectId group by w.status")
    List<Object[]> countByStatus(@Param("projectId") UUID projectId);
}
