package com.payflow.payment.method;

import com.payflow.payment.PaymentMethodType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// Concept: Factory + Strategy. Spring injects every PaymentMethod bean; the factory indexes them by type.
// At runtime the request's "method" picks the strategy (polymorphism: pay() runs the subclass's steps).
// Adding a new method (say UPI) means one new class; this factory and PaymentService do not change (Open/Closed).
@Component
public class PaymentMethodFactory {

    private final Map<PaymentMethodType, PaymentMethod> byType;

    public PaymentMethodFactory(List<PaymentMethod> methods) {
        this.byType = methods.stream().collect(Collectors.toMap(PaymentMethod::type, Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException("two payment methods for " + a.type());
                },
                () -> new EnumMap<>(PaymentMethodType.class)));
    }

    public PaymentMethod forType(PaymentMethodType type) {
        PaymentMethod method = byType.get(type);
        if (method == null) {
            throw new IllegalArgumentException("unsupported payment method " + type);
        }
        return method;
    }
}
