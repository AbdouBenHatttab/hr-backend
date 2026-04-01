package tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.event.NotificationEvent;
import tn.isetbizerte.pfe.hrbackend.modules.notification.service.NotificationService;

@Service
@Slf4j
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public NotificationEventConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "${app.kafka.topic.notification-events}")
    public void handleNotificationEvent(String payload) {
        try {
            NotificationEvent event = objectMapper.readValue(payload, NotificationEvent.class);
            notificationService.createNotification(
                    event.getUserId(),
                    event.getMessage(),
                    event.getType(),
                    event.getReferenceType(),
                    event.getReferenceId(),
                    event.getActionUrl()
            );
        } catch (Exception e) {
            log.error("Failed to process notification event payload", e);
        }
    }
}
