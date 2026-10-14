package com.payflow.wallet;

import java.time.Instant;

public record BalancePoint(Instant createdAt, long balanceAfterMinor) {
}
