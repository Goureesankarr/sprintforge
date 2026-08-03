package dev.sreedaya.sprintforge.sprint;

import dev.sreedaya.sprintforge.common.ApiExceptionHandler.ConflictException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SprintServiceTest {
    private final SprintService service = new SprintService(
            mock(SprintRepository.class),
            mock(dev.sreedaya.sprintforge.project.ProjectAccessService.class),
            mock(dev.sreedaya.sprintforge.audit.AuditEventRepository.class),
            new SimpleMeterRegistry());

    @Test
    void allowsPlannedSprintToStart() {
        assertDoesNotThrow(() -> service.validateTransition(
                Sprint.Status.PLANNED, Sprint.Status.ACTIVE));
    }

    @Test
    void allowsActiveSprintToComplete() {
        assertDoesNotThrow(() -> service.validateTransition(
                Sprint.Status.ACTIVE, Sprint.Status.COMPLETED));
    }

    @Test
    void rejectsChangesToTerminalSprint() {
        assertThrows(ConflictException.class, () -> service.validateTransition(
                Sprint.Status.COMPLETED, Sprint.Status.ACTIVE));
    }
}
