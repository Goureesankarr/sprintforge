package dev.sreedaya.sprintforge.task;

import dev.sreedaya.sprintforge.common.ApiExceptionHandler.ConflictException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkItemWorkflowTest {
    private final WorkItemWorkflow workflow = new WorkItemWorkflow();

    @Test
    void allowsForwardWorkflowTransitions() {
        assertDoesNotThrow(() -> workflow.validateTransition(
                WorkItem.Status.TODO, WorkItem.Status.IN_PROGRESS));
        assertDoesNotThrow(() -> workflow.validateTransition(
                WorkItem.Status.IN_PROGRESS, WorkItem.Status.IN_REVIEW));
        assertDoesNotThrow(() -> workflow.validateTransition(
                WorkItem.Status.IN_REVIEW, WorkItem.Status.DONE));
    }

    @Test
    void rejectsSkippingWorkflowStages() {
        assertThrows(ConflictException.class, () -> workflow.validateTransition(
                WorkItem.Status.BACKLOG, WorkItem.Status.DONE));
    }

    @Test
    void allowsReopeningCompletedWork() {
        assertDoesNotThrow(() -> workflow.validateTransition(
                WorkItem.Status.DONE, WorkItem.Status.IN_REVIEW));
    }
}
