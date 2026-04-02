package tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.event.NotificationEvent;
import tn.isetbizerte.pfe.hrbackend.infrastructure.outbox.OutboxEventService;

@Service
@Slf4j
public class NotificationEventProducer {

    private final ObjectMapper objectMapper;
    private final String notificationEventsTopic;
    private final OutboxEventService outboxEventService;

    public NotificationEventProducer(
            ObjectMapper objectMapper,
            OutboxEventService outboxEventService,
            @Value("${app.kafka.topic.notification-events}") String notificationEventsTopic
    ) {
        this.objectMapper = objectMapper;
        this.outboxEventService = outboxEventService;
        this.notificationEventsTopic = notificationEventsTopic;
    }

    public void publish(NotificationEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxEventService.enqueue(notificationEventsTopic, event.getUserId(), payload);
            log.info("Enqueued notification event for userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to enqueue notification event for userId={}", event.getUserId(), e);
            throw new RuntimeException("Failed to enqueue notification event", e);
        }
    }
}
