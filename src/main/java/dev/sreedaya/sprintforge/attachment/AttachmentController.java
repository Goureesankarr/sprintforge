package dev.sreedaya.sprintforge.attachment;

import dev.sreedaya.sprintforge.common.ApiExceptionHandler.NotFoundException;
import dev.sreedaya.sprintforge.project.Project;
import dev.sreedaya.sprintforge.project.ProjectAccessService;
import dev.sreedaya.sprintforge.realtime.ProjectEventPublisher;
import dev.sreedaya.sprintforge.task.WorkItem;
import dev.sreedaya.sprintforge.task.WorkItemRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/v1/projects/{projectId}/attachments")
public class AttachmentController {
    private final AttachmentRepository attachments;
    private final AttachmentStorage storage;
    private final ProjectAccessService access;
    private final WorkItemRepository workItems;
    private final ProjectEventPublisher events;

    public AttachmentController(
            AttachmentRepository attachments,
            AttachmentStorage storage,
            ProjectAccessService access,
            WorkItemRepository workItems,
            ProjectEventPublisher events) {
        this.attachments = attachments;
        this.storage = storage;
        this.access = access;
        this.workItems = workItems;
        this.events = events;
    }

    public record CreateUploadRequest(
            @NotBlank @Size(max = 255) String fileName,
            @NotBlank
                    @Pattern(regexp = "^(image/(png|jpeg|gif|webp)|application/pdf|text/plain)$")
                    String contentType,
            @Min(1) @Max(26_214_400) long sizeBytes,
            UUID workItemId) {}

    public record UploadTicket(
            UUID attachmentId,
            URI uploadUrl,
            Map<String, String> requiredHeaders,
            Instant expiresAt) {
        public UploadTicket {
            requiredHeaders = Map.copyOf(requiredHeaders);
        }

        @Override
        public Map<String, String> requiredHeaders() {
            return Map.copyOf(requiredHeaders);
        }
    }

    public record AttachmentView(
            UUID id,
            UUID projectId,
            UUID workItemId,
            String fileName,
            String contentType,
            long sizeBytes,
            String status,
            UUID uploadedBy,
            Instant createdAt) {
        static AttachmentView from(Attachment attachment) {
            return new AttachmentView(
                    attachment.getId(),
                    attachment.getProject().getId(),
                    attachment.getWorkItem() == null ? null : attachment.getWorkItem().getId(),
                    attachment.getFileName(),
                    attachment.getContentType(),
                    attachment.getSizeBytes(),
                    attachment.getStatus().name(),
                    attachment.getUploadedBy().getId(),
                    attachment.getCreatedAt());
        }
    }

    @PostMapping("/upload-url")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    UploadTicket createUpload(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateUploadRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Project project = access.requireMember(projectId, jwt);
        WorkItem workItem = request.workItemId() == null
                ? null
                : workItems.findActiveByIdAndProjectId(request.workItemId(), projectId)
                        .orElseThrow(() -> new NotFoundException("Work item not found"));

        UUID attachmentId = UUID.randomUUID();
        String safeFileName = request.fileName().trim().replaceAll("[^A-Za-z0-9._-]", "_");
        String objectKey = "projects/" + projectId + "/" + attachmentId + "/" + safeFileName;
        AttachmentStorage.PresignedUpload upload = storage.presignUpload(
                objectKey, request.contentType(), request.sizeBytes());

        Attachment attachment = new Attachment();
        attachment.setId(attachmentId);
        attachment.setProject(project);
        attachment.setWorkItem(workItem);
        attachment.setUploadedBy(access.currentUser(jwt));
        attachment.setObjectKey(objectKey);
        attachment.setFileName(safeFileName);
        attachment.setContentType(request.contentType());
        attachment.setSizeBytes(request.sizeBytes());
        attachment.setStatus(Attachment.Status.PENDING);
        attachment.setCreatedAt(Instant.now());
        attachments.save(attachment);
        return new UploadTicket(
                attachmentId, upload.url(), upload.headers(), upload.expiresAt());
    }

    @PatchMapping("/{attachmentId}/complete")
    @Transactional
    AttachmentView complete(
            @PathVariable UUID projectId,
            @PathVariable UUID attachmentId,
            @AuthenticationPrincipal Jwt jwt) {
        access.requireMember(projectId, jwt);
        Attachment attachment = attachments.findByIdAndProjectId(attachmentId, projectId)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        attachment.setStatus(Attachment.Status.AVAILABLE);
        events.publish(projectId, "ATTACHMENT_AVAILABLE", "ATTACHMENT", attachmentId);
        return AttachmentView.from(attachment);
    }

    @GetMapping
    @Transactional
    Page<AttachmentView> list(
            @PathVariable UUID projectId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        access.requireMember(projectId, jwt);
        return attachments.findByProjectIdOrderByCreatedAtDesc(projectId, pageable)
                .map(AttachmentView::from);
    }
}
