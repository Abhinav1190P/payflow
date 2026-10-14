package com.payflow.wallet;

// Concept: sealed interface. The compiler knows the only two possible outcomes, so a switch over them
// needs no default branch and fails to compile if a new outcome is added and not handled.
public sealed interface DebitOutcome {

    record Posted(LedgerTransaction transaction, long balanceAfterMinor) implements DebitOutcome {
    }

    record Rejected(String code, String reason) implements DebitOutcome {
    }
}
