package com.payflow.messaging.outbox;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Polls the outbox and publishes to Kafka. Delivery is at-least-once: if the app crashes after Kafka accepted
// a message but before published_at is saved, it is sent again, which is why every consumer is idempotent.
@Component
@ConditionalOnProperty(name = "payflow.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 100;

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;

    public OutboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafka, Clock clock) {
        this.repository = repository;
        this.kafka = kafka;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${payflow.outbox.poll-ms:300}")
    @Transactional
    public void publishPending() {
        List<OutboxMessage> batch = repository.lockNextBatch(BATCH);
        for (OutboxMessage message : batch) {
            try {
                kafka.send(message.getTopic(), message.getMessageKey(), message.getPayload()).get(5, TimeUnit.SECONDS);
                message.markPublished(clock.instant());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                message.markFailed("interrupted");
                return;
            } catch (Exception e) {
                message.markFailed(e.getMessage());
                log.warn("outbox publish failed for {} (attempt {}): {}", message.getId(), message.getAttempts(), e.getMessage());
                return;
            }
        }
    }
}
