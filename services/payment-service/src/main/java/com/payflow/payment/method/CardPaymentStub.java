package com.payflow.payment.method;

import com.payflow.payment.PaymentMethodType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

// A stand-in for a real card gateway: no money moves. Test cards (all pass the Luhn check):
// ending 0002 is declined, ending 0010 simulates a gateway outage, anything else is approved.
@Component
public class CardPaymentStub extends PaymentMethod {

    public CardPaymentStub() {
        super(FeePolicy.basisPoints(200).cappedAt(5_000));
    }

    @Override
    public PaymentMethodType type() {
        return PaymentMethodType.CARD;
    }

    @Override
    protected Optional<String> validate(PaymentCommand command) {
        Optional<String> base = super.validate(command);
        if (base.isPresent()) {
            return base;
        }
        String card = command.cardNumber();
        if (card == null || !card.matches("\\d{12,19}") || !passesLuhn(card)) {
            return Optional.of("Card number is invalid");
        }
        return Optional.empty();
    }

    @Override
    protected PaymentResult charge(PaymentCommand command, long feeMinor) {
        String card = command.cardNumber();
        if (card.endsWith("0002")) {
            return new PaymentResult.Declined("CARD_DECLINED", "The card issuer declined the payment");
        }
        if (card.endsWith("0010")) {
            return new PaymentResult.Failed("Card gateway did not respond");
        }
        return new PaymentResult.Approved("card_" + UUID.randomUUID(), null, command.amountMinor(), feeMinor, null);
    }

    // Luhn checksum: from the right, double every second digit (subtract 9 if over 9), the sum must end in 0.
    static boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}
