package tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.event.NotificationEvent;
import tn.isetbizerte.pfe.hrbackend.infrastructure.inbox.ProcessedEventService;
import tn.isetbizerte.pfe.hrbackend.modules.notification.service.NotificationService;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@Slf4j
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final ProcessedEventService processedEventService;

    public NotificationEventConsumer(ObjectMapper objectMapper,
                                     NotificationService notificationService,
                                     ProcessedEventService processedEventService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.processedEventService = processedEventService;
    }

    @KafkaListener(topics = "${app.kafka.topic.notification-events}")
    @Transactional
    public void handleNotificationEvent(String payload) {
        try {
            NotificationEvent event = objectMapper.readValue(payload, NotificationEvent.class);
            String dedupKey = resolveDedupKey(event);
            boolean firstTime = processedEventService.tryMarkProcessed(dedupKey, "notification-events");
            if (!firstTime) {
                log.warn("Skipping duplicate notification event: {}", dedupKey);
                return;
            }
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
            throw new IllegalStateException("Notification event processing failed", e);
        }
    }

    private String resolveDedupKey(NotificationEvent event) {
        if (event.getEventId() != null && !event.getEventId().isBlank()) {
            return event.getEventId();
        }
        String signature = String.join("|",
                value(event.getUserId()),
                value(event.getType()),
                value(event.getReferenceType()),
                value(event.getReferenceId()),
                value(event.getMessage()),
                value(event.getActionUrl())
        );
        return "notification:" + UUID.nameUUIDFromBytes(signature.getBytes(StandardCharsets.UTF_8));
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
