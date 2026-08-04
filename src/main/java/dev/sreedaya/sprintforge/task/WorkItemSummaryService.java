package dev.sreedaya.sprintforge.task;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemSummaryService {
    private final WorkItemRepository workItems;

    public WorkItemSummaryService(WorkItemRepository workItems) {
        this.workItems = workItems;
    }

    @Cacheable(cacheNames = "boardSummaries", key = "#projectId", sync = true)
    @Transactional(readOnly = true)
    public WorkItemController.BoardSummary summarize(UUID projectId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (WorkItem.Status status : WorkItem.Status.values()) {
            counts.put(status.name(), 0L);
        }
        workItems.countByStatus(projectId).forEach(row ->
                counts.put(((WorkItem.Status) row[0]).name(), (Long) row[1]));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new WorkItemController.BoardSummary(counts, total);
    }
}
