package com.payflow.wallet.dto;

import java.time.Instant;

public record BalanceAtResponse(Instant at, long balanceMinor, boolean hadActivity) {
}
