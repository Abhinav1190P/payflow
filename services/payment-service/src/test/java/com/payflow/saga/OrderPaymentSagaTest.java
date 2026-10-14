package com.payflow.saga;

import static org.assertj.core.api.Assertions.assertThat;

import com.payflow.common.events.PaymentCommand;
import com.payflow.messaging.outbox.OutboxMessage;
import com.payflow.messaging.outbox.OutboxRepository;
import com.payflow.support.IntegrationTest;
import com.payflow.wallet.WalletQueryService;
import com.payflow.wallet.WalletService;
import com.payflow.wallet.dto.TopUpRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrderPaymentSagaTest extends IntegrationTest {

    @Autowired
    private OrderPaymentService payments;
    @Autowired
    private WalletService walletService;
    @Autowired
    private WalletQueryService queries;
    @Autowired
    private OutboxRepository outbox;

    private List<String> repliesFor(UUID orderId) {
        return outbox.findAll().stream()
                .filter(m -> m.getMessageKey().equals(orderId.toString()))
                .map(OutboxMessage::getPayload)
                .toList();
    }

    @Test
    void chargeIsIdempotentAndRefundReturnsMoney() {
        UUID user = UUID.randomUUID();
        walletService.topUp(user, "seed-" + UUID.randomUUID(), new TopUpRequest(5_000));
        UUID orderId = UUID.randomUUID();
        var charge = new PaymentCommand.ChargePayment("m-" + UUID.randomUUID(), orderId, user, 1_200);

        payments.charge(charge);
        payments.charge(charge);

        assertThat(queries.myWallet(user).balanceMinor()).isEqualTo(3_800);
        assertThat(repliesFor(orderId)).hasSize(1).first().asString().contains("PaymentCompleted");

        payments.refund(new PaymentCommand.RefundPayment("m-" + UUID.randomUUID(), orderId, user, 1_200));
        assertThat(queries.myWallet(user).balanceMinor()).isEqualTo(5_000);
        assertThat(repliesFor(orderId)).anySatisfy(p -> assertThat(p).contains("PaymentRefunded"));
    }

    @Test
    void insufficientFundsRepliesPaymentFailed() {
        UUID user = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        payments.charge(new PaymentCommand.ChargePayment("m-" + UUID.randomUUID(), orderId, user, 999));
        assertThat(repliesFor(orderId)).singleElement().asString().contains("PaymentFailed");
        assertThat(queries.myWallet(user).balanceMinor()).isZero();
    }
}
