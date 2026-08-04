package dev.sreedaya.sprintforge.realtime;

import java.time.Instant;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProjectEventPublisher {
    private final SimpMessagingTemplate messaging;

    public ProjectEventPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void publish(UUID projectId, String type, String entityType, UUID entityId) {
        messaging.convertAndSend(
                "/topic/projects/" + projectId,
                new ProjectEvent(type, entityType, entityId, Instant.now()));
    }

    public record ProjectEvent(
            String type, String entityType, UUID entityId, Instant occurredAt) {}
}
