package tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.modules.auth.service.PasswordResetEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for password reset events
 */
@Service
public class PasswordResetKafkaProducer {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetKafkaProducer.class);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.kafka.topic.password-reset}")
    private String passwordResetTopic;

    /**
     * Publish password reset event to Kafka
     */
    public void publishPasswordResetEvent(PasswordResetEvent event) {
        logger.info("Publishing PasswordResetEvent for email: {}", event.getEmail());

        try {
            String eventJson = objectMapper.writeValueAsString(event);

            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(
                    passwordResetTopic,
                    event.getEmail(),
                    eventJson
            );

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    logger.info("✅ Successfully published password reset event for: {} to topic: {} offset: {}",
                            event.getEmail(),
                            passwordResetTopic,
                            result.getRecordMetadata().offset());
                } else {
                    logger.error("❌ Failed to publish password reset event for: {}", event.getEmail(), ex);
                }
            });

        } catch (Exception e) {
            logger.error("❌ Error serializing password reset event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to publish password reset event", e);
        }
    }
}

