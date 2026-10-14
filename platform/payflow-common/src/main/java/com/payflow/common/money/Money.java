package com.payflow.common.money;

import java.math.BigDecimal;
import java.util.Objects;

// Concept: immutability + record. A record's fields are final, so a Money value can be shared across threads safely.
// Concept: equals/hashCode contract. Records generate both from all components, so equal values always hash equally.
// Concept: Comparable. Money has a natural order (by amount) so it can be sorted and compared.
public record Money(long minor, String currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be an ISO-4217 code");
        }
    }

    public static Money of(long minor, String currency) {
        return new Money(minor, currency);
    }

    public static Money zero(String currency) {
        return new Money(0, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minor, other.minor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minor, other.minor), currency);
    }

    public boolean isNegative() {
        return minor < 0;
    }

    public boolean isPositive() {
        return minor > 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(minor, other.minor);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    @Override
    public String toString() {
        return currency + " " + BigDecimal.valueOf(minor, 2).toPlainString();
    }
}
