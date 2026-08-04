package dev.sreedaya.sprintforge.notification;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean enabled;
    private final String from;

    public EmailNotificationService(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.notifications.email.enabled:false}") boolean enabled,
            @Value("${app.notifications.email.from:noreply@sprintforge.dev}") String from) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
    }

    @Async
    public void projectInvitation(String recipient, String projectName, UUID projectId) {
        if (!enabled) {
            log.debug("Email disabled; project invitation not sent: projectId={}", projectId);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("You were added to " + projectName);
        message.setText("You now have access to project " + projectName
                + " in SprintForge. Project ID: " + projectId);
        try {
            JavaMailSender sender = mailSender.getIfAvailable();
            if (sender == null) {
                log.error("Email notifications are enabled but no mail sender is configured");
                return;
            }
            sender.send(message);
        } catch (RuntimeException exception) {
            log.error("Project invitation email failed: projectId={}", projectId, exception);
        }
    }
}
