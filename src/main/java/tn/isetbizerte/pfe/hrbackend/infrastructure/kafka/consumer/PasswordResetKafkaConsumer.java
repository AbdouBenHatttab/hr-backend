package tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.event.PasswordResetEvent;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.PasswordResetEmailService;

/**
 * Kafka consumer for password reset events
 * Listens to password-reset topic and triggers email sending
 */
@Service
public class PasswordResetKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final PasswordResetEmailService passwordResetEmailService;
    private final tn.isetbizerte.pfe.hrbackend.infrastructure.email.HREmailService hrEmailService;

    public PasswordResetKafkaConsumer(
            ObjectMapper objectMapper,
            PasswordResetEmailService passwordResetEmailService,
            tn.isetbizerte.pfe.hrbackend.infrastructure.email.HREmailService hrEmailService) {
        this.objectMapper             = objectMapper;
        this.passwordResetEmailService = passwordResetEmailService;
        this.hrEmailService           = hrEmailService;
    }

    @KafkaListener(topics = "${app.kafka.topic.password-reset}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumePasswordResetEvent(String eventJson) {
        logger.info("Received password reset event from Kafka");

        try {
            PasswordResetEvent event = objectMapper.readValue(eventJson, PasswordResetEvent.class);
            logger.info("Parsed password reset event for '{}'", event.getEmail());
            // Use new professional email service
            String expiresAt = event.getExpiryTime() != null
                    ? event.getExpiryTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm 'on' dd/MM/yyyy"))
                    : "15 minutes from now";
            hrEmailService.sendPasswordReset(
                    event.getEmail(), event.getFirstName(), event.getLastName(),
                    event.getResetToken(), expiresAt);
            logger.info("Password reset email processing completed for '{}'", event.getEmail());

        } catch (Exception e) {
            logger.error("Error processing password reset event payload", e);
        }
    }
}

