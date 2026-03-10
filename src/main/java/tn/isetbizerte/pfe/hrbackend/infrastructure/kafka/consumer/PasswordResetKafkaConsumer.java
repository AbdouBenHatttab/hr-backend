package tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.PasswordResetEmailService;
import tn.isetbizerte.pfe.hrbackend.modules.auth.service.PasswordResetEvent;

/**
 * Kafka consumer for password reset events
 * Listens to password-reset topic and triggers email sending
 */
@Service
public class PasswordResetKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetKafkaConsumer.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordResetEmailService passwordResetEmailService;

    /**
     * Listen to password-reset topic and send reset email
     */
    @KafkaListener(topics = "${app.kafka.topic.password-reset}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumePasswordResetEvent(String eventJson) {
        logger.info("📩 Received password reset event from Kafka");

        try {
            PasswordResetEvent event = objectMapper.readValue(eventJson, PasswordResetEvent.class);
            logger.info("✅ Successfully parsed password reset event for: {}", event.getEmail());

            // Send password reset email
            passwordResetEmailService.sendPasswordResetEmail(event);

            logger.info("✅ Password reset email processing completed for: {}", event.getEmail());

        } catch (Exception e) {
            logger.error("❌ Error processing password reset event: {}", e.getMessage(), e);
        }
    }
}

