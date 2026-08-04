package dev.sreedaya.sprintforge.attachment;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    Optional<Attachment> findByIdAndProjectId(UUID id, UUID projectId);

    Page<Attachment> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);
}
