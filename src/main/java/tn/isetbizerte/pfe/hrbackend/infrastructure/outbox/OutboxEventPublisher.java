package tn.isetbizerte.pfe.hrbackend.infrastructure.outbox;

import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OutboxEventPublisher {

    private final OutboxEventRepository repository;
    private final OutboxEventService outboxEventService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int maxAttempts;
    private final long retryBaseMs;
    private final long retryMaxMs;
    private final Counter publishSuccess;
    private final Counter publishFailure;
    private final Counter publishRetry;

    public OutboxEventPublisher(
            OutboxEventRepository repository,
            OutboxEventService outboxEventService,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry,
            @Value("${app.outbox.max-attempts:5}") int maxAttempts,
            @Value("${app.outbox.retry-base-ms:2000}") long retryBaseMs,
            @Value("${app.outbox.retry-max-ms:60000}") long retryMaxMs
    ) {
        this.repository = repository;
        this.outboxEventService = outboxEventService;
        this.kafkaTemplate = kafkaTemplate;
        this.maxAttempts = maxAttempts;
        this.retryBaseMs = retryBaseMs;
        this.retryMaxMs = retryMaxMs;
        this.publishSuccess = meterRegistry.counter("outbox.publish.success");
        this.publishFailure = meterRegistry.counter("outbox.publish.failure");
        this.publishRetry = meterRegistry.counter("outbox.publish.retry");
    }

    @Scheduled(fixedDelayString = "${app.outbox.publish-interval-ms:2000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.findReadyByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                LocalDateTime.now(),
                PageRequest.of(0, 100)
        );
        if (batch.isEmpty()) return;

        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()).get();
                outboxEventService.markSent(event);
                publishSuccess.increment();
                log.info("Outbox published eventId={} topic={}", event.getId(), event.getTopic());
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                if (event.getAttempts() + 1 >= maxAttempts) {
                    outboxEventService.markFailed(event, msg);
                    publishFailure.increment();
                    log.error("Outbox failed eventId={} topic={} attempts={}", event.getId(), event.getTopic(), event.getAttempts());
                } else {
                    long backoffMs = computeBackoffMs(event.getAttempts() + 1);
                    outboxEventService.markPending(event, msg, LocalDateTime.now().plus(Duration.ofMillis(backoffMs)));
                    publishRetry.increment();
                    log.warn("Outbox retry eventId={} topic={} attempts={}", event.getId(), event.getTopic(), event.getAttempts());
                }
            }
        }
    }

    private long computeBackoffMs(int nextAttempt) {
        long exp = retryBaseMs * (1L << Math.max(0, nextAttempt - 1));
        return Math.min(exp, retryMaxMs);
    }
}
