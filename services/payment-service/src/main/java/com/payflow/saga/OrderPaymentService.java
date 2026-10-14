package com.payflow.saga;

import com.payflow.common.events.OrderReply;
import com.payflow.common.events.PaymentCommand;
import com.payflow.common.events.Topics;
import com.payflow.messaging.inbox.Inbox;
import com.payflow.messaging.outbox.OutboxWriter;
import com.payflow.wallet.DebitOutcome;
import com.payflow.wallet.LedgerTransaction;
import com.payflow.wallet.WalletService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Saga participant. Each handler is ONE local transaction: mark the message processed (inbox), change the
// ledger, and write the reply to the outbox. Either all three happen or none do.
@Service
public class OrderPaymentService {

    static final String CONSUMER = "payment-saga";

    private final Inbox inbox;
    private final OutboxWriter outbox;
    private final WalletService wallets;
    private final OrderPaymentRepository payments;
    private final Clock clock;

    public OrderPaymentService(Inbox inbox, OutboxWriter outbox, WalletService wallets, OrderPaymentRepository payments, Clock clock) {
        this.inbox = inbox;
        this.outbox = outbox;
        this.wallets = wallets;
        this.payments = payments;
        this.clock = clock;
    }

    @Transactional
    public void charge(PaymentCommand.ChargePayment command) {
        if (!inbox.firstTime(CONSUMER, command.messageId())) {
            return;
        }
        var existing = payments.findById(command.orderId());
        if (existing.isPresent()) {
            reply(command.orderId(), new OrderReply.PaymentCompleted(newId(), command.orderId(), existing.get().getTransactionId()));
            return;
        }
        DebitOutcome outcome = wallets.tryPayMerchant(command.userId(), command.amountMinor(), "order:" + command.orderId());
        switch (outcome) {
            case DebitOutcome.Posted posted -> {
                payments.save(new OrderPayment(command.orderId(), command.userId(), command.amountMinor(),
                        posted.transaction().getId(), clock.instant()));
                reply(command.orderId(), new OrderReply.PaymentCompleted(newId(), command.orderId(), posted.transaction().getId()));
            }
            case DebitOutcome.Rejected rejected ->
                    reply(command.orderId(), new OrderReply.PaymentFailed(newId(), command.orderId(), rejected.reason()));
        }
    }

    @Transactional
    public void refund(PaymentCommand.RefundPayment command) {
        if (!inbox.firstTime(CONSUMER, command.messageId())) {
            return;
        }
        payments.findById(command.orderId())
                .filter(p -> p.getStatus() == OrderPayment.Status.CHARGED)
                .ifPresent(p -> {
                    LedgerTransaction refund = wallets.refundFromMerchant(p.getUserId(), p.getAmountMinor(), "refund:" + p.getOrderId());
                    p.markRefunded(refund.getId());
                });
        reply(command.orderId(), new OrderReply.PaymentRefunded(newId(), command.orderId()));
    }

    // Called by the retry scheduler when a charge kept failing for technical reasons: give the saga a clear answer.
    @Transactional
    public void failAfterRetries(PaymentCommand.ChargePayment command) {
        if (inbox.firstTime(CONSUMER, command.messageId())) {
            reply(command.orderId(), new OrderReply.PaymentFailed(newId(), command.orderId(), "Payment temporarily unavailable"));
        }
    }

    private void reply(UUID orderId, OrderReply reply) {
        outbox.enqueue(Topics.ORDER_REPLIES, orderId.toString(), reply);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
