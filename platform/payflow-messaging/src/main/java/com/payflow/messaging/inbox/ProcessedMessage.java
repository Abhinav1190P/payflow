package com.payflow.messaging.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "processed_messages")
public class ProcessedMessage {

    @EmbeddedId
    private Key id;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedMessage() {
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "consumer", nullable = false)
        private String consumer;

        @Column(name = "message_id", nullable = false)
        private String messageId;

        protected Key() {
        }

        public Key(String consumer, String messageId) {
            this.consumer = consumer;
            this.messageId = messageId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(consumer, k.consumer) && Objects.equals(messageId, k.messageId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(consumer, messageId);
        }
    }
}
