package tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.event.PasswordResetEvent;
import tn.isetbizerte.pfe.hrbackend.infrastructure.outbox.OutboxEventService;

/**
 * Kafka producer for password reset events
 */
@Service
public class PasswordResetKafkaProducer {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetKafkaProducer.class);

    private final ObjectMapper objectMapper;
    private final String passwordResetTopic;
    private final OutboxEventService outboxEventService;

    public PasswordResetKafkaProducer(
            ObjectMapper objectMapper,
            OutboxEventService outboxEventService,
            @Value("${app.kafka.topic.password-reset}") String passwordResetTopic
    ) {
        this.objectMapper = objectMapper;
        this.outboxEventService = outboxEventService;
        this.passwordResetTopic = passwordResetTopic;
    }

    public void publishPasswordResetEvent(PasswordResetEvent event) {
        logger.info("Publishing PasswordResetEvent for email: {}", event.getEmail());

        try {
            String eventJson = objectMapper.writeValueAsString(event);
            outboxEventService.enqueue(passwordResetTopic, event.getEmail(), eventJson);
            logger.info("Enqueued password reset event for '{}' to outbox", event.getEmail());

        } catch (Exception e) {
            logger.error("Failed to serialize password reset event for '{}'", event.getEmail(), e);
            throw new RuntimeException("Failed to enqueue password reset event", e);
        }
    }
}
