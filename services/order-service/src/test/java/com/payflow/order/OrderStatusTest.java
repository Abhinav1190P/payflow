package com.payflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void happyPath() {
        assertThat(OrderStatus.PENDING.moveTo(OrderStatus.PAID)).isEqualTo(OrderStatus.PAID);
        assertThat(OrderStatus.PAID.moveTo(OrderStatus.CONFIRMED)).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(OrderStatus.CONFIRMED.isTerminal()).isTrue();
    }

    @Test
    void compensationPath() {
        assertThat(OrderStatus.PAID.canMoveTo(OrderStatus.REFUNDING)).isTrue();
        assertThat(OrderStatus.REFUNDING.canMoveTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
    }

    @Test
    void illegalTransitionsAreRejected() {
        assertThatThrownBy(() -> OrderStatus.CANCELLED.moveTo(OrderStatus.PAID)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OrderStatus.PENDING.moveTo(OrderStatus.CONFIRMED)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OrderStatus.CONFIRMED.moveTo(OrderStatus.REFUNDING)).isInstanceOf(IllegalStateException.class);
    }
}
