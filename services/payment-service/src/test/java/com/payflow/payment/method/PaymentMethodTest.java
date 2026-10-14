package com.payflow.payment.method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payflow.payment.PaymentMethodType;
import com.payflow.wallet.DebitOutcome;
import com.payflow.wallet.LedgerTransaction;
import com.payflow.wallet.TransactionType;
import com.payflow.wallet.WalletService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentMethodTest {

    private final WalletService walletService = mock(WalletService.class);
    private final WalletPayment wallet = new WalletPayment(walletService);
    private final CardPaymentStub card = new CardPaymentStub();
    private final PaymentMethodFactory factory = new PaymentMethodFactory(List.of(wallet, card));
    private final UUID user = UUID.randomUUID();

    @Test
    void factoryPicksStrategyByType() {
        assertThat(factory.forType(PaymentMethodType.WALLET)).isSameAs(wallet);
        assertThat(factory.forType(PaymentMethodType.CARD)).isSameAs(card);
    }

    @Test
    void factoryRejectsDuplicateStrategies() {
        assertThatThrownBy(() -> new PaymentMethodFactory(List.of(card, new CardPaymentStub())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void walletPaymentApprovesWhenLedgerPosts() {
        var tx = new LedgerTransaction(TransactionType.PAYMENT, "order-1", Instant.now());
        when(walletService.tryPayMerchant(eq(user), eq(500L), eq("order-1"))).thenReturn(new DebitOutcome.Posted(tx, 1500));

        PaymentResult result = wallet.pay(new PaymentCommand(user, 500, "order-1", null));

        assertThat(result).isInstanceOfSatisfying(PaymentResult.Approved.class, a -> {
            assertThat(a.amountMinor()).isEqualTo(500);
            assertThat(a.feeMinor()).isZero();
            assertThat(a.balanceAfterMinor()).isEqualTo(1500L);
        });
    }

    @Test
    void walletPaymentDeclinesWhenLedgerRejects() {
        when(walletService.tryPayMerchant(eq(user), anyLong(), anyString()))
                .thenReturn(new DebitOutcome.Rejected("INSUFFICIENT_FUNDS", "too low"));
        PaymentResult result = wallet.pay(new PaymentCommand(user, 500, "order-2", null));
        assertThat(result).isEqualTo(new PaymentResult.Declined("INSUFFICIENT_FUNDS", "too low"));
    }

    @Test
    void templateMethodValidatesBeforeCharging() {
        PaymentResult result = wallet.pay(new PaymentCommand(user, 0, "order-3", null));
        assertThat(result).isInstanceOf(PaymentResult.Declined.class);
        verify(walletService, never()).tryPayMerchant(eq(user), anyLong(), anyString());
    }

    @Test
    void cardStubOutcomesAndFee() {
        assertThat(card.pay(new PaymentCommand(user, 10_000, "o", "4242424242424242")))
                .isInstanceOfSatisfying(PaymentResult.Approved.class, a -> assertThat(a.feeMinor()).isEqualTo(200));
        assertThat(card.pay(new PaymentCommand(user, 10_000, "o", "4000000000000002")))
                .isEqualTo(new PaymentResult.Declined("CARD_DECLINED", "The card issuer declined the payment"));
        assertThat(card.pay(new PaymentCommand(user, 10_000, "o", "4000000000000010")))
                .isInstanceOf(PaymentResult.Failed.class);
        assertThat(card.pay(new PaymentCommand(user, 10_000, "o", "4242424242424241")))
                .isInstanceOf(PaymentResult.Declined.class);
    }

    @Test
    void feePolicyCompositionWithLambdasAndDefaultMethods() {
        FeePolicy twoPercent = FeePolicy.basisPoints(200);
        assertThat(twoPercent.feeFor(10_000)).isEqualTo(200);
        assertThat(twoPercent.cappedAt(5_000).feeFor(1_000_000)).isEqualTo(5_000);
        FeePolicy flat = amount -> 25;
        assertThat(flat.cappedAt(10).feeFor(999)).isEqualTo(10);
        assertThat(FeePolicy.NONE.feeFor(123)).isZero();
    }

    @Test
    void luhnCheck() {
        assertThat(CardPaymentStub.passesLuhn("4242424242424242")).isTrue();
        assertThat(CardPaymentStub.passesLuhn("4242424242424241")).isFalse();
    }
}
