package com.payflow.stock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.events.InventoryCommand;
import com.payflow.common.events.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryCommandListener {

    private final ObjectMapper objectMapper;
    private final StockService stock;

    public InventoryCommandListener(ObjectMapper objectMapper, StockService stock) {
        this.objectMapper = objectMapper;
        this.stock = stock;
    }

    @KafkaListener(topics = Topics.INVENTORY_COMMANDS, groupId = "inventory-service")
    public void onMessage(String json) throws JsonProcessingException {
        InventoryCommand command = objectMapper.readValue(json, InventoryCommand.class);
        switch (command) {
            case InventoryCommand.ReserveStock reserve -> stock.reserve(reserve);
        }
    }
}
