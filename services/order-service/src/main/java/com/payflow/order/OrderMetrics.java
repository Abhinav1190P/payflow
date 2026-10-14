package com.payflow.order;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

// Custom business metric for Prometheus/Grafana: payflow_orders_total{status="CONFIRMED"} and so on.
@Component
public class OrderMetrics {

    private final Map<OrderStatus, Counter> counters = new EnumMap<>(OrderStatus.class);

    public OrderMetrics(MeterRegistry registry) {
        for (OrderStatus status : OrderStatus.values()) {
            counters.put(status, Counter.builder("payflow.orders")
                    .description("Orders that reached each status")
                    .tag("status", status.name())
                    .register(registry));
        }
    }

    public void reached(OrderStatus status) {
        counters.get(status).increment();
    }
}
