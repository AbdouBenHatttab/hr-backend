package tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tn.isetbizerte.pfe.hrbackend.common.event.UserRoleAssignedEvent;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.EmailNotificationService;

/**
 * Kafka consumer for handling domain events.
 * Listens on user-role-assigned topic and triggers email notifications.
 */
@Component
public class UserRoleEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(UserRoleEventConsumer.class);

    private final EmailNotificationService emailNotificationService;
    private final tn.isetbizerte.pfe.hrbackend.infrastructure.email.HREmailService hrEmailService;
    private final ObjectMapper objectMapper;

    public UserRoleEventConsumer(
            EmailNotificationService emailNotificationService,
            tn.isetbizerte.pfe.hrbackend.infrastructure.email.HREmailService hrEmailService,
            ObjectMapper objectMapper) {
        this.emailNotificationService = emailNotificationService;
        this.hrEmailService           = hrEmailService;
        this.objectMapper             = objectMapper;
    }

    @PostConstruct
    public void init() {
        logger.info("✅ UserRoleEventConsumer initialized - listening on topic: user-role-assigned");
    }

    @KafkaListener(topics = "${app.kafka.topic.user-role-assigned}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleUserRoleAssignedEvent(String eventJson) {
        logger.info("📩 Received UserRoleAssignedEvent: {}", eventJson);

        try {
            UserRoleAssignedEvent event = objectMapper.readValue(eventJson, UserRoleAssignedEvent.class);

            if (event.getEmail() == null || event.getEmail().isEmpty()) {
                logger.warn("⚠️ Cannot send email - no email address for user: {}", event.getUsername());
                return;
            }

            // Use new professional email service
            hrEmailService.sendRoleAssigned(
                    event.getEmail(), event.getFirstName(), event.getLastName(),
                    event.getUsername(), event.getNewRole());

            logger.info("📧 Email notification triggered for user: {} with new role: {}",
                    event.getUsername(), event.getNewRole());

        } catch (Exception e) {
            logger.error("❌ Error handling UserRoleAssignedEvent: {}", e.getMessage(), e);
        }
    }
}
