package com.payflow.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.BusinessRuleException;
import com.payflow.common.error.ConflictException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Concept: idempotent POST. The client sends an Idempotency-Key header. The first request runs and its
// response is stored in the same database transaction as the money movement. A retry with the same key
// gets the stored response back instead of moving money twice.
@Service
public class IdempotencyService {

    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_-]{8,100}");

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;

    // Concept: ConcurrentHashMap as an in-flight registry. putIfAbsent is atomic, so if two identical
    // requests arrive at the same moment on this instance, only one runs and the other gets 409 at once.
    // It is only a fast first line of defence: the (user_id, idem_key) primary key in the database is the
    // real guarantee, and it also works across several app instances.
    private final ConcurrentHashMap<String, Boolean> inFlight = new ConcurrentHashMap<>();

    public IdempotencyService(IdempotencyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // Concept: @Transactional propagation MANDATORY. This must run inside the caller's transaction so the
    // stored response and the money movement commit or roll back together; calling it without one fails fast.
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> Replayable<T> run(UUID userId, String key, String fingerprint, Class<T> type, Supplier<Replayable<T>> action) {
        if (key == null || !VALID_KEY.matcher(key).matches()) {
            throw new BusinessRuleException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 8-100 characters of letters, digits, - or _");
        }
        String slot = userId + ":" + key;
        if (inFlight.putIfAbsent(slot, Boolean.TRUE) != null) {
            throw new ConflictException("IDEMPOTENCY_IN_PROGRESS", "A request with this Idempotency-Key is still being processed");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                inFlight.remove(slot);
            }
        });

        var existing = repository.findById(new IdempotencyRecord.Key(userId, key));
        if (existing.isPresent()) {
            var stored = existing.get();
            if (!stored.getRequestHash().equals(fingerprint)) {
                throw new BusinessRuleException("IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was already used for a different request");
            }
            return new Replayable<>(stored.getResponseStatus(), read(stored.getResponseBody(), type), true);
        }

        Replayable<T> result = action.get();
        repository.save(new IdempotencyRecord(userId, key, fingerprint, result.status(), write(result.body())));
        return result;
    }

    int inFlightCount() {
        return inFlight.size();
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored idempotent response is unreadable", e);
        }
    }

    private String write(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("response cannot be stored", e);
        }
    }
}
