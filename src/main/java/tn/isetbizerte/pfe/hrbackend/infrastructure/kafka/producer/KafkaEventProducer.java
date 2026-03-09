package tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import tn.isetbizerte.pfe.hrbackend.common.event.UserRoleAssignedEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for publishing domain events.
 * Uses Spring Boot auto-configured KafkaTemplate.
 */
@Component
public class KafkaEventProducer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic.user-role-assigned}")
    private String userRoleAssignedTopic;

    public KafkaEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a UserRoleAssignedEvent to Kafka as JSON string.
     */
    public void publishUserRoleAssignedEvent(UserRoleAssignedEvent event) {
        try {
            logger.info("Publishing UserRoleAssignedEvent for user: {} with new role: {}",
                    event.getUsername(), event.getNewRole());

            String eventJson = objectMapper.writeValueAsString(event);

            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(userRoleAssignedTopic, event.getUserId().toString(), eventJson);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    logger.info("✅ Successfully published event for user: {} to topic: {} offset: {}",
                            event.getUsername(),
                            userRoleAssignedTopic,
                            result.getRecordMetadata().offset());
                } else {
                    logger.error("❌ Failed to publish event for user: {}: {}",
                            event.getUsername(), ex.getMessage());
                }
            });

        } catch (JsonProcessingException e) {
            logger.error("❌ Error serializing event for user: {}: {}",
                    event.getUsername(), e.getMessage());
        } catch (Exception e) {
            logger.error("❌ Error publishing event for user: {}: {}",
                    event.getUsername(), e.getMessage());
        }
    }
}
