package com.payflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payflow.common.error.BusinessRuleException;
import com.payflow.common.error.InsufficientFundsException;
import com.payflow.common.security.AuthenticatedUser;
import com.payflow.common.security.Role;
import com.payflow.order.client.CatalogClient;
import com.payflow.order.client.CheckoutGateway;
import com.payflow.order.dto.OrderView;
import com.payflow.order.dto.PlaceOrderRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CheckoutServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderWriter writer = mock(OrderWriter.class);
    private final CheckoutGateway gateway = mock(CheckoutGateway.class);
    private final CheckoutService checkout = new CheckoutService(orders, writer, gateway, Executors.newVirtualThreadPerTaskExecutor());
    private final AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "u@payflow.dev", Role.USER);
    private final UUID mouse = UUID.randomUUID();
    private final UUID hub = UUID.randomUUID();

    private void catalog() {
        when(gateway.prices(any())).thenReturn(List.of(
                new CatalogClient.ProductView(mouse, "S1", "Mouse", 500, 10),
                new CatalogClient.ProductView(hub, "S2", "Hub", 2_000, 10)));
        when(orders.findByUserIdAndIdempotencyKey(any(), anyString())).thenReturn(Optional.empty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mergesDuplicateLinesAndCreatesOrder() {
        catalog();
        when(gateway.balance(anyString())).thenReturn(OptionalLong.of(100_000));
        var view = new OrderView(UUID.randomUUID(), OrderStatus.PENDING, 3_500, null, Instant.now(), Instant.now(), List.of());
        when(writer.create(eq(user.id()), eq("checkout-key-1"), anyMap(), anyMap())).thenReturn(view);

        var result = checkout.placeOrder(user, "Bearer t", "checkout-key-1", new PlaceOrderRequest(List.of(
                new PlaceOrderRequest.Line(mouse, 1), new PlaceOrderRequest.Line(hub, 1), new PlaceOrderRequest.Line(mouse, 2))));

        assertThat(result.replayed()).isFalse();
        ArgumentCaptor<Map<UUID, Integer>> quantities = ArgumentCaptor.forClass(Map.class);
        verify(writer).create(eq(user.id()), eq("checkout-key-1"), quantities.capture(), anyMap());
        assertThat(quantities.getValue()).containsEntry(mouse, 3).containsEntry(hub, 1);
    }

    @Test
    void rejectsWhenBalanceTooLowWithoutCreatingOrder() {
        catalog();
        when(gateway.balance(anyString())).thenReturn(OptionalLong.of(1_000));
        assertThatThrownBy(() -> checkout.placeOrder(user, "Bearer t", "checkout-key-2",
                new PlaceOrderRequest(List.of(new PlaceOrderRequest.Line(hub, 1)))))
                .isInstanceOf(InsufficientFundsException.class);
        verify(writer, never()).create(any(), anyString(), anyMap(), anyMap());
    }

    @Test
    void unknownBalanceStillAllowsCheckout() {
        catalog();
        when(gateway.balance(anyString())).thenReturn(OptionalLong.empty());
        checkout.placeOrder(user, "Bearer t", "checkout-key-3", new PlaceOrderRequest(List.of(new PlaceOrderRequest.Line(hub, 1))));
        verify(writer).create(eq(user.id()), eq("checkout-key-3"), anyMap(), anyMap());
    }

    @Test
    void unknownProductIsRejected() {
        catalog();
        when(gateway.balance(anyString())).thenReturn(OptionalLong.of(100_000));
        assertThatThrownBy(() -> checkout.placeOrder(user, "Bearer t", "checkout-key-4",
                new PlaceOrderRequest(List.of(new PlaceOrderRequest.Line(UUID.randomUUID(), 1)))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Unknown products");
    }
}
