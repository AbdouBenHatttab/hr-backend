package tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.event.NotificationEvent;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class NotificationEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String notificationEventsTopic;

    public NotificationEventProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topic.notification-events}") String notificationEventsTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.notificationEventsTopic = notificationEventsTopic;
    }

    public void publish(NotificationEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(notificationEventsTopic, event.getUserId(), payload);
            future.get();
            log.info("Published notification event to {} for userId={}", notificationEventsTopic, event.getUserId());
        } catch (Exception e) {
            log.error("Failed to publish notification event to topic {}", notificationEventsTopic, e);
            throw new RuntimeException("Failed to publish notification event to Kafka", e);
        }
    }
}
