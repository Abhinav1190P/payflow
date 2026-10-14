package com.payflow.stock;

import static org.assertj.core.api.Assertions.assertThat;

import com.payflow.catalog.Product;
import com.payflow.catalog.ProductRepository;
import com.payflow.common.events.InventoryCommand;
import com.payflow.messaging.outbox.OutboxMessage;
import com.payflow.messaging.outbox.OutboxRepository;
import com.payflow.support.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StockServiceTest {

    @Autowired
    private StockService stock;
    @Autowired
    private ProductRepository products;
    @Autowired
    private OutboxRepository outbox;

    private Product product(int stockLevel) {
        String sku = "T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return products.save(new Product(sku, "Test " + sku, 1000, stockLevel));
    }

    private List<String> replies(UUID orderId) {
        return outbox.findAll().stream().filter(m -> m.getMessageKey().equals(orderId.toString()))
                .map(OutboxMessage::getPayload).toList();
    }

    @Test
    void reservesAllLinesOrNone() {
        Product plenty = product(10);
        Product scarce = product(1);

        UUID rejectedOrder = UUID.randomUUID();
        stock.reserve(new InventoryCommand.ReserveStock("m-" + UUID.randomUUID(), rejectedOrder,
                List.of(new InventoryCommand.Line(plenty.getId(), 3), new InventoryCommand.Line(scarce.getId(), 2))));
        assertThat(replies(rejectedOrder)).singleElement().asString().contains("StockRejected");
        assertThat(products.findById(plenty.getId()).orElseThrow().getStock()).isEqualTo(10);

        UUID okOrder = UUID.randomUUID();
        var command = new InventoryCommand.ReserveStock("m-" + UUID.randomUUID(), okOrder,
                List.of(new InventoryCommand.Line(plenty.getId(), 3), new InventoryCommand.Line(scarce.getId(), 1)));
        stock.reserve(command);
        stock.reserve(command);
        assertThat(replies(okOrder)).singleElement().asString().contains("StockReserved");
        assertThat(products.findById(plenty.getId()).orElseThrow().getStock()).isEqualTo(7);
        assertThat(products.findById(scarce.getId()).orElseThrow().getStock()).isZero();
    }
}
