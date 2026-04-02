package tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.event.RequestEvent;
import tn.isetbizerte.pfe.hrbackend.infrastructure.outbox.OutboxEventService;

@Service
@Slf4j
public class RequestEventProducer {

    private final ObjectMapper objectMapper;
    private final String requestEventsTopic;
    private final OutboxEventService outboxEventService;

    public RequestEventProducer(
            ObjectMapper objectMapper,
            OutboxEventService outboxEventService,
            @Value("${app.kafka.topic.request-events}") String requestEventsTopic
    ) {
        this.objectMapper = objectMapper;
        this.outboxEventService = outboxEventService;
        this.requestEventsTopic = requestEventsTopic;
    }

    public void publish(RequestEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxEventService.enqueue(requestEventsTopic, String.valueOf(event.getRequestId()), payload);
            log.info("Enqueued {} to outbox for eventId={} requestId={}", event.getType(), event.getEventId(), event.getRequestId());
        } catch (Exception e) {
            log.error("Failed to enqueue request event for requestId={}", event.getRequestId(), e);
            throw new RuntimeException("Failed to enqueue request event", e);
        }
    }
}
