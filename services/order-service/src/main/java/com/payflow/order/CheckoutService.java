package com.payflow.order;

import com.payflow.common.error.BusinessRuleException;
import com.payflow.common.error.InsufficientFundsException;
import com.payflow.common.security.AuthenticatedUser;
import com.payflow.order.client.CatalogClient;
import com.payflow.order.client.CheckoutGateway;
import com.payflow.order.dto.OrderView;
import com.payflow.order.dto.PlaceOrderRequest;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

// Checkout: price the cart and check the balance IN PARALLEL, then create the order and start the saga.
// No database transaction is open during the remote calls; only OrderWriter.create() is transactional.
@Service
public class CheckoutService {

    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_-]{8,100}");

    public record CheckoutResult(OrderView order, boolean replayed) {
    }

    private final OrderRepository orders;
    private final OrderWriter writer;
    private final CheckoutGateway gateway;
    private final ExecutorService ioExecutor;

    public CheckoutService(OrderRepository orders, OrderWriter writer, CheckoutGateway gateway,
                           @Qualifier("ioExecutor") ExecutorService ioExecutor) {
        this.orders = orders;
        this.writer = writer;
        this.gateway = gateway;
        this.ioExecutor = ioExecutor;
    }

    public CheckoutResult placeOrder(AuthenticatedUser user, String authorization, String idempotencyKey, PlaceOrderRequest request) {
        if (idempotencyKey == null || !VALID_KEY.matcher(idempotencyKey).matches()) {
            throw new BusinessRuleException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 8-100 characters of letters, digits, - or _");
        }
        var existing = orders.findByUserIdAndIdempotencyKey(user.id(), idempotencyKey);
        if (existing.isPresent()) {
            return new CheckoutResult(OrderView.from(existing.get()), true);
        }

        // Concept: Streams toMap with a merge function: the same product twice in the cart becomes one line.
        Map<UUID, Integer> quantities = request.items().stream()
                .collect(Collectors.toMap(PlaceOrderRequest.Line::productId, PlaceOrderRequest.Line::quantity, Integer::sum));
        List<UUID> productIds = List.copyOf(quantities.keySet());

        // Concept: CompletableFuture fan-out. Both calls start at once on virtual threads; total wait is
        // roughly the slower of the two, not their sum.
        CompletableFuture<List<CatalogClient.ProductView>> pricesFuture =
                CompletableFuture.supplyAsync(() -> gateway.prices(productIds), ioExecutor);
        CompletableFuture<OptionalLong> balanceFuture =
                CompletableFuture.supplyAsync(() -> gateway.balance(authorization), ioExecutor);

        List<CatalogClient.ProductView> products;
        OptionalLong balance;
        try {
            CompletableFuture.allOf(pricesFuture, balanceFuture).join();
            products = pricesFuture.join();
            balance = balanceFuture.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }

        Map<UUID, CatalogClient.ProductView> byId = products.stream()
                .collect(Collectors.toMap(CatalogClient.ProductView::id, Function.identity()));
        List<UUID> unknown = productIds.stream().filter(id -> !byId.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            throw new BusinessRuleException("UNKNOWN_PRODUCT", "Unknown products: " + unknown);
        }
        long total = quantities.entrySet().stream()
                .mapToLong(e -> Math.multiplyExact(byId.get(e.getKey()).priceMinor(), (long) e.getValue()))
                .sum();
        if (balance.isPresent() && balance.getAsLong() < total) {
            throw new InsufficientFundsException();
        }
        return new CheckoutResult(writer.create(user.id(), idempotencyKey, quantities, byId), false);
    }
}
