package tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.event.RequestEvent;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class RequestEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String requestEventsTopic;

    public RequestEventProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topic.request-events}") String requestEventsTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.requestEventsTopic = requestEventsTopic;
    }

    public void publish(RequestEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(requestEventsTopic, String.valueOf(event.getRequestId()), payload);
            future.get();
            log.info("Published {} to {} for requestId={}", event.getType(), requestEventsTopic, event.getRequestId());
        } catch (Exception e) {
            log.error("Failed to publish request event to topic {}", requestEventsTopic, e);
            throw new RuntimeException("Failed to publish request event to Kafka", e);
        }
    }
}
