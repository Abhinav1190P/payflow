package com.payflow.payment.method;

import com.payflow.payment.PaymentMethodType;
import com.payflow.wallet.DebitOutcome;
import com.payflow.wallet.WalletService;
import org.springframework.stereotype.Component;

@Component
public class WalletPayment extends PaymentMethod {

    private final WalletService walletService;

    public WalletPayment(WalletService walletService) {
        super(FeePolicy.NONE);
        this.walletService = walletService;
    }

    @Override
    public PaymentMethodType type() {
        return PaymentMethodType.WALLET;
    }

    @Override
    protected PaymentResult charge(PaymentCommand command, long feeMinor) {
        DebitOutcome outcome = walletService.tryPayMerchant(command.userId(), command.amountMinor(), command.merchantReference());
        return switch (outcome) {
            case DebitOutcome.Posted posted -> new PaymentResult.Approved("wallet_" + posted.transaction().getId(),
                    posted.transaction().getId(), command.amountMinor(), feeMinor, posted.balanceAfterMinor());
            case DebitOutcome.Rejected rejected -> new PaymentResult.Declined(rejected.code(), rejected.reason());
        };
    }
}
