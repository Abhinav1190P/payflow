package com.payflow.messaging.inbox;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Concept: idempotent consumer. Kafka (and our outbox) deliver at-least-once, so the same message can arrive
// twice. Recording the message id in the same transaction as the business change means a duplicate finds its
// id already there (the INSERT affects 0 rows) and is skipped.
@Component
public class Inbox {

    private final InboxRepository repository;

    public Inbox(InboxRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(String consumer, String messageId) {
        return repository.insertIfAbsent(consumer, messageId) == 1;
    }
}
