package tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.event.DecisionEvent;

import java.util.concurrent.CompletableFuture;

@Service
public class DecisionEventProducer {

    private static final Logger logger = LoggerFactory.getLogger(DecisionEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private final String leaveEventsTopic;
    private final String loanEventsTopic;
    private final String documentEventsTopic;
    private final String authorizationEventsTopic;

    public DecisionEventProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topic.leave-events}") String leaveEventsTopic,
            @Value("${app.kafka.topic.loan-events}") String loanEventsTopic,
            @Value("${app.kafka.topic.document-events}") String documentEventsTopic,
            @Value("${app.kafka.topic.authorization-events}") String authorizationEventsTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.leaveEventsTopic = leaveEventsTopic;
        this.loanEventsTopic = loanEventsTopic;
        this.documentEventsTopic = documentEventsTopic;
        this.authorizationEventsTopic = authorizationEventsTopic;
    }

    public void publishLeaveEvent(DecisionEvent event) {
        publish(leaveEventsTopic, event);
    }

    public void publishLoanEvent(DecisionEvent event) {
        publish(loanEventsTopic, event);
    }

    public void publishDocumentEvent(DecisionEvent event) {
        publish(documentEventsTopic, event);
    }

    public void publishAuthorizationEvent(DecisionEvent event) {
        publish(authorizationEventsTopic, event);
    }

    private void publish(String topic, DecisionEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(topic, String.valueOf(event.getRequestId()), payload);

            // Force publish to succeed or fail the transaction
            future.get();
            logger.info("Published {} to {} for requestId={}", event.getType(), topic, event.getRequestId());
        } catch (Exception e) {
            logger.error("Failed to publish {} to topic {}", event.getType(), topic, e);
            throw new RuntimeException("Failed to publish decision event to Kafka", e);
        }
    }
}
