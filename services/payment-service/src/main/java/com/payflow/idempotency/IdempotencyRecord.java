package com.payflow.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord implements Persistable<IdempotencyRecord.Key> {

    @EmbeddedId
    private Key id;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Persistable lets Spring Data call persist() directly for a new record with an assigned id,
    // instead of merge(), which would first run a SELECT to check whether the row exists.
    @Transient
    private boolean isNew = true;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(UUID userId, String key, String requestHash, int responseStatus, String responseBody) {
        this.id = new Key(userId, key);
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.createdAt = Instant.now();
    }

    @Override
    public Key getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @Column(name = "idem_key", nullable = false)
        private String idemKey;

        protected Key() {
        }

        public Key(UUID userId, String idemKey) {
            this.userId = userId;
            this.idemKey = idemKey;
        }

        public UUID userId() {
            return userId;
        }

        public String idemKey() {
            return idemKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key other)) {
                return false;
            }
            return Objects.equals(userId, other.userId) && Objects.equals(idemKey, other.idemKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, idemKey);
        }
    }
}
