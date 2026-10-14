package com.payflow.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Concept: transactional outbox. Instead of sending to Kafka inside a business transaction (and risking
// "DB committed but message lost" or "message sent but DB rolled back"), the message is inserted into the
// outbox table in the SAME transaction as the business change. A separate relay sends it afterwards.
@Component
public class OutboxWriter {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxWriter(OutboxRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String topic, String key, Object message) {
        try {
            repository.save(new OutboxMessage(topic, key, objectMapper.writeValueAsString(message), clock.instant()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialise outbox message", e);
        }
    }
}
