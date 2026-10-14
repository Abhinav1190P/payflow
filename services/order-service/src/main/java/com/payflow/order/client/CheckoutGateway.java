package com.payflow.order.client;

import com.payflow.common.error.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Concept: Resilience4j (Retry + CircuitBreaker + RateLimiter) around the two remote calls checkout makes.
// The fallbacks differ on purpose:
// - prices are required, so if inventory-service is down the checkout fails fast with 503;
// - the balance is only an early "you can't afford this" check (payment-service re-checks during the saga),
//   so if payment-service is down we degrade gracefully and skip it.
@Component
public class CheckoutGateway {

    private static final Logger log = LoggerFactory.getLogger(CheckoutGateway.class);

    private final CatalogClient catalog;
    private final WalletClient wallets;

    public CheckoutGateway(CatalogClient catalog, WalletClient wallets) {
        this.catalog = catalog;
        this.wallets = wallets;
    }

    @Retry(name = "inventory-service")
    @CircuitBreaker(name = "inventory-service", fallbackMethod = "pricesUnavailable")
    @RateLimiter(name = "inventory-service")
    public List<CatalogClient.ProductView> prices(List<UUID> productIds) {
        return catalog.batch(productIds).data();
    }

    @Retry(name = "payment-service")
    @CircuitBreaker(name = "payment-service", fallbackMethod = "balanceUnknown")
    public OptionalLong balance(String authorization) {
        return OptionalLong.of(wallets.myWallet(authorization).data().balanceMinor());
    }

    private List<CatalogClient.ProductView> pricesUnavailable(List<UUID> productIds, Throwable cause) {
        log.warn("inventory-service unavailable: {}", cause.toString());
        throw new ServiceUnavailableException("Catalog is unavailable, try again shortly");
    }

    private OptionalLong balanceUnknown(String authorization, Throwable cause) {
        log.warn("payment-service unavailable, skipping early balance check: {}", cause.toString());
        return OptionalLong.empty();
    }
}
