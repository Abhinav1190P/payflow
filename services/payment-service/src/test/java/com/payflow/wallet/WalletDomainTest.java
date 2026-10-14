package com.payflow.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.payflow.common.error.BusinessRuleException;
import com.payflow.common.error.InsufficientFundsException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WalletDomainTest {

    private Wallet wallet() {
        return Wallet.openFor(java.util.UUID.randomUUID(), "INR");
    }

    @Test
    void creditAndDebitChangeBalance() {
        Wallet w = wallet();
        w.credit(1000);
        w.debit(400);
        assertThat(w.getBalanceMinor()).isEqualTo(600);
        assertThat(w.balance().toString()).isEqualTo("INR 6.00");
    }

    @Test
    void userWalletCannotOverdraw() {
        Wallet w = wallet();
        w.credit(100);
        assertThatThrownBy(() -> w.debit(101)).isInstanceOf(InsufficientFundsException.class);
        assertThat(w.getBalanceMinor()).isEqualTo(100);
    }

    @Test
    void amountsMustBePositive() {
        Wallet w = wallet();
        assertThatThrownBy(() -> w.credit(0)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> w.debit(-5)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void frozenWalletRejectsMovements() {
        Wallet w = wallet();
        w.credit(100);
        w.freeze();
        assertThatThrownBy(() -> w.debit(10)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("frozen");
        w.unfreeze();
        w.debit(10);
        assertThat(w.getBalanceMinor()).isEqualTo(90);
    }

    @Test
    void directionBehaviour() {
        assertThat(Direction.CREDIT.apply(100, 50)).isEqualTo(150);
        assertThat(Direction.DEBIT.apply(100, 50)).isEqualTo(50);
        assertThat(Direction.DEBIT.opposite()).isEqualTo(Direction.CREDIT);
    }

    @Test
    void ledgerTransactionMustBalance() {
        Wallet from = wallet();
        Wallet to = wallet();
        Instant now = Instant.now();
        var tx = new LedgerTransaction(TransactionType.TRANSFER, "t", now);
        tx.addEntry(LedgerEntry.builder().transaction(tx).wallet(from).direction(Direction.DEBIT).amountMinor(100).createdAt(now).build());
        assertThatThrownBy(tx::assertBalanced).isInstanceOf(IllegalStateException.class);
        tx.addEntry(LedgerEntry.builder().transaction(tx).wallet(to).direction(Direction.CREDIT).amountMinor(90).createdAt(now).build());
        assertThatThrownBy(tx::assertBalanced).isInstanceOf(IllegalStateException.class);

        var balanced = new LedgerTransaction(TransactionType.TRANSFER, "t", now);
        balanced.addEntry(LedgerEntry.builder().transaction(balanced).wallet(from).direction(Direction.DEBIT).amountMinor(100).createdAt(now).build());
        balanced.addEntry(LedgerEntry.builder().transaction(balanced).wallet(to).direction(Direction.CREDIT).amountMinor(100).createdAt(now).build());
        balanced.assertBalanced();
    }

    @Test
    void builderValidatesRequiredFields() {
        assertThatThrownBy(() -> LedgerEntry.builder().amountMinor(10).build()).isInstanceOf(NullPointerException.class);
        var tx = new LedgerTransaction(TransactionType.TOP_UP, null, Instant.now());
        assertThatThrownBy(() -> LedgerEntry.builder().transaction(tx).wallet(wallet()).direction(Direction.CREDIT)
                .createdAt(Instant.now()).amountMinor(0).build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unsavedEntitiesAreNeverEqual() {
        assertThat(wallet()).isNotEqualTo(wallet());
        Wallet same = wallet();
        assertThat(same).isEqualTo(same);
    }

    @Test
    void csvEscapesCommasQuotesAndFormulaInjection() {
        assertThat(StatementCsvWriter.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(StatementCsvWriter.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(StatementCsvWriter.escape("=SUM(A1)")).isEqualTo("'=SUM(A1)");
        assertThat(StatementCsvWriter.escape(null)).isEmpty();
    }

    @Test
    void acceptHeaderParsing() {
        assertThat(WalletController.wantsCsv("text/csv")).isTrue();
        assertThat(WalletController.wantsCsv("text/csv, application/json;q=0.5")).isTrue();
        assertThat(WalletController.wantsCsv("*/*")).isFalse();
        assertThat(WalletController.wantsCsv("application/json")).isFalse();
        assertThat(WalletController.wantsCsv(null)).isFalse();
        assertThat(WalletController.wantsCsv("application/json, text/csv;q=0.1")).isFalse();
        assertThat(WalletController.wantsCsv("text/*")).isTrue();
    }
}
