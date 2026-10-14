package com.payflow.stock;

import com.payflow.catalog.Product;
import com.payflow.catalog.ProductRepository;
import com.payflow.catalog.ProductService;
import com.payflow.common.events.InventoryCommand;
import com.payflow.common.events.OrderReply;
import com.payflow.common.events.Topics;
import com.payflow.messaging.inbox.Inbox;
import com.payflow.messaging.outbox.OutboxWriter;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Saga participant: reserve every line of an order, or none of them (all-or-nothing in one local transaction).
@Service
public class StockService {

    static final String CONSUMER = "inventory-saga";

    private final ProductRepository products;
    private final StockReservationRepository reservations;
    private final Inbox inbox;
    private final OutboxWriter outbox;
    private final CacheManager cacheManager;
    private final Clock clock;

    public StockService(ProductRepository products, StockReservationRepository reservations, Inbox inbox, OutboxWriter outbox,
                        CacheManager cacheManager, Clock clock) {
        this.products = products;
        this.reservations = reservations;
        this.inbox = inbox;
        this.outbox = outbox;
        this.cacheManager = cacheManager;
        this.clock = clock;
    }

    @Transactional
    public void reserve(InventoryCommand.ReserveStock command) {
        if (!inbox.firstTime(CONSUMER, command.messageId())) {
            return;
        }
        if (reservations.existsForOrder(command.orderId())) {
            reply(command.orderId(), new OrderReply.StockReserved(newId(), command.orderId()));
            return;
        }
        Map<UUID, Integer> wanted = command.lines().stream()
                .collect(Collectors.toMap(InventoryCommand.Line::productId, InventoryCommand.Line::quantity, Integer::sum));
        Map<UUID, Product> locked = products.findAllForUpdate(wanted.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Optional<String> problem = wanted.entrySet().stream()
                .filter(e -> !locked.containsKey(e.getKey()) || !locked.get(e.getKey()).canReserve(e.getValue()))
                .map(e -> locked.containsKey(e.getKey()) ? "Not enough stock for " + locked.get(e.getKey()).getName()
                        : "Unknown product " + e.getKey())
                .findFirst();
        if (problem.isPresent()) {
            reply(command.orderId(), new OrderReply.StockRejected(newId(), command.orderId(), problem.get()));
            return;
        }
        wanted.forEach((productId, quantity) -> {
            locked.get(productId).reserve(quantity);
            reservations.save(new StockReservation(command.orderId(), productId, quantity, clock.instant()));
        });
        evictCached(List.copyOf(wanted.keySet()));
        reply(command.orderId(), new OrderReply.StockReserved(newId(), command.orderId()));
    }

    private void evictCached(List<UUID> productIds) {
        var cache = cacheManager.getCache(ProductService.PRODUCT_CACHE);
        if (cache != null) {
            productIds.forEach(cache::evict);
        }
    }

    private void reply(UUID orderId, OrderReply reply) {
        outbox.enqueue(Topics.ORDER_REPLIES, orderId.toString(), reply);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
