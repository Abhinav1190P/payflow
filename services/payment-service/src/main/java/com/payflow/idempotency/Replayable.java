package com.payflow.idempotency;

// The result of an idempotent operation: HTTP status + body, and whether it was replayed from storage.
public record Replayable<T>(int status, T body, boolean replayed) {

    public static <T> Replayable<T> fresh(int status, T body) {
        return new Replayable<>(status, body, false);
    }
}
