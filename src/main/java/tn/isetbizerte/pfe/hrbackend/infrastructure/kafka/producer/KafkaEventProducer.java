package tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tn.isetbizerte.pfe.hrbackend.common.event.UserRoleAssignedEvent;
import tn.isetbizerte.pfe.hrbackend.infrastructure.outbox.OutboxEventService;

/**
 * Kafka producer for publishing domain events.
 * Uses Spring Boot auto-configured KafkaTemplate.
 */
@Component
public class KafkaEventProducer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaEventProducer.class);

    private final ObjectMapper objectMapper;
    private final OutboxEventService outboxEventService;

    @Value("${app.kafka.topic.user-role-assigned}")
    private String userRoleAssignedTopic;

    public KafkaEventProducer(ObjectMapper objectMapper, OutboxEventService outboxEventService) {
        this.objectMapper = objectMapper;
        this.outboxEventService = outboxEventService;
    }

    /**
     * Publishes a UserRoleAssignedEvent to Kafka as JSON string.
     */
    public void publishUserRoleAssignedEvent(UserRoleAssignedEvent event) {
        try {
            logger.info("Publishing UserRoleAssignedEvent for user: {} with new role: {}",
                    event.getUsername(), event.getNewRole());

            String eventJson = objectMapper.writeValueAsString(event);
            outboxEventService.enqueue(userRoleAssignedTopic, event.getUserId().toString(), eventJson);
            logger.info("Enqueued UserRoleAssignedEvent for user: {}", event.getUsername());

        } catch (JsonProcessingException e) {
            logger.error("❌ Error serializing event for user: {}: {}",
                    event.getUsername(), e.getMessage());
        } catch (Exception e) {
            logger.error("❌ Error enqueueing event for user: {}: {}",
                    event.getUsername(), e.getMessage());
        }
    }
}
