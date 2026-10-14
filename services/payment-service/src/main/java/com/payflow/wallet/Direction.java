package com.payflow.wallet;

// Concept: enum with behaviour. Each constant overrides apply(), so the balance rule lives with the
// constant instead of in an if/else spread through the services.
public enum Direction {
    DEBIT {
        @Override
        public long apply(long balance, long amount) {
            return Math.subtractExact(balance, amount);
        }
    },
    CREDIT {
        @Override
        public long apply(long balance, long amount) {
            return Math.addExact(balance, amount);
        }
    };

    public abstract long apply(long balance, long amount);

    public Direction opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
