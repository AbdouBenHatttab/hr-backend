package tn.isetbizerte.pfe.hrbackend.infrastructure.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Slf4j
public class OutboxEventPublisher {

    private final OutboxEventRepository repository;
    private final OutboxEventService outboxEventService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int maxAttempts;

    public OutboxEventPublisher(
            OutboxEventRepository repository,
            OutboxEventService outboxEventService,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.outbox.max-attempts:5}") int maxAttempts
    ) {
        this.repository = repository;
        this.outboxEventService = outboxEventService;
        this.kafkaTemplate = kafkaTemplate;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${app.outbox.publish-interval-ms:2000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        if (batch.isEmpty()) return;

        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()).get();
                outboxEventService.markSent(event);
                log.info("Outbox published eventId={} topic={}", event.getId(), event.getTopic());
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                if (event.getAttempts() + 1 >= maxAttempts) {
                    outboxEventService.markFailed(event, msg);
                    log.error("Outbox failed eventId={} topic={} attempts={}", event.getId(), event.getTopic(), event.getAttempts());
                } else {
                    outboxEventService.markPending(event, msg);
                    log.warn("Outbox retry eventId={} topic={} attempts={}", event.getId(), event.getTopic(), event.getAttempts());
                }
            }
        }
    }
}
