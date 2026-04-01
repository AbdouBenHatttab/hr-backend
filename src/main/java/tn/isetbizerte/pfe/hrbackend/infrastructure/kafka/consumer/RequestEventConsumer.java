package tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.event.NotificationEvent;
import tn.isetbizerte.pfe.hrbackend.common.event.RequestEvent;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.NotificationEventProducer;

@Service
@Slf4j
public class RequestEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationEventProducer notificationEventProducer;

    public RequestEventConsumer(ObjectMapper objectMapper, NotificationEventProducer notificationEventProducer) {
        this.objectMapper = objectMapper;
        this.notificationEventProducer = notificationEventProducer;
    }

    @KafkaListener(topics = "${app.kafka.topic.request-events}")
    public void handleRequestEvent(String payload) {
        try {
            RequestEvent event = objectMapper.readValue(payload, RequestEvent.class);
            NotificationEvent notif = new NotificationEvent(
                    event.getEmployeeId(),
                    buildMessage(event),
                    event.getType()
            );
            notificationEventProducer.publish(notif);
        } catch (Exception e) {
            log.error("Failed to process request event payload", e);
        }
    }

    private String buildMessage(RequestEvent event) {
        String type = event.getType();
        if (type == null) return "Request updated.";
        return switch (type) {
            case "LEAVE_APPROVED" -> "Your leave request was approved.";
            case "LEAVE_REJECTED" -> "Your leave request was rejected.";
            case "LOAN_APPROVED" -> "Your loan request was approved.";
            case "LOAN_REJECTED" -> "Your loan request was rejected.";
            case "DOCUMENT_APPROVED" -> "Your document request was approved.";
            case "DOCUMENT_REJECTED" -> "Your document request was rejected.";
            case "AUTH_APPROVED" -> "Your authorization request was approved.";
            case "AUTH_REJECTED" -> "Your authorization request was rejected.";
            default -> "Request updated.";
        };
    }
}
