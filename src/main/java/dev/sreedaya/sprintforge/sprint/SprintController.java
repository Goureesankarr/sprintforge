package dev.sreedaya.sprintforge.sprint;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/sprints")
public class SprintController {
    private final SprintService sprintService;

    public SprintController(SprintService sprintService) {
        this.sprintService = sprintService;
    }

    public record CreateSprintRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String goal,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) {}

    public record ChangeStatusRequest(@NotNull Sprint.Status status) {}

    public record SprintView(
            UUID id,
            UUID projectId,
            String name,
            String goal,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            Instant createdAt,
            Instant updatedAt,
            long version) {
        static SprintView from(Sprint sprint) {
            return new SprintView(
                    sprint.getId(),
                    sprint.getProject().getId(),
                    sprint.getName(),
                    sprint.getGoal(),
                    sprint.getStatus().name(),
                    sprint.getStartDate(),
                    sprint.getEndDate(),
                    sprint.getCreatedAt(),
                    sprint.getUpdatedAt(),
                    sprint.getVersion());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SprintView create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateSprintRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Sprint sprint = sprintService.create(
                projectId,
                request.name(),
                request.goal(),
                request.startDate(),
                request.endDate(),
                jwt);
        return SprintView.from(sprint);
    }

    @GetMapping
    List<SprintView> list(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal Jwt jwt) {
        return sprintService.list(projectId, jwt).stream()
                .map(SprintView::from)
                .toList();
    }

    @PatchMapping("/{sprintId}/status")
    SprintView changeStatus(
            @PathVariable UUID projectId,
            @PathVariable UUID sprintId,
            @Valid @RequestBody ChangeStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return SprintView.from(
                sprintService.changeStatus(projectId, sprintId, request.status(), jwt));
    }
}
