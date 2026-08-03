package dev.sreedaya.sprintforge.task;

import dev.sreedaya.sprintforge.common.ApiExceptionHandler.ConflictException;
import org.springframework.stereotype.Service;

@Service
public class WorkItemWorkflow {
    public void validateTransition(
            WorkItem.Status current, WorkItem.Status requested) {
        if (current == requested) {
            return;
        }
        boolean valid = switch (current) {
            case BACKLOG -> requested == WorkItem.Status.TODO;
            case TODO -> requested == WorkItem.Status.BACKLOG
                    || requested == WorkItem.Status.IN_PROGRESS;
            case IN_PROGRESS -> requested == WorkItem.Status.TODO
                    || requested == WorkItem.Status.IN_REVIEW;
            case IN_REVIEW -> requested == WorkItem.Status.IN_PROGRESS
                    || requested == WorkItem.Status.DONE;
            case DONE -> requested == WorkItem.Status.IN_REVIEW;
        };
        if (!valid) {
            throw new ConflictException(
                    "Work item cannot move from " + current + " to " + requested);
        }
    }
}
