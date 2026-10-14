package com.payflow.order.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.events.OrderReply;
import com.payflow.common.events.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderReplyListener {

    private final ObjectMapper objectMapper;
    private final OrderSagaOrchestrator orchestrator;

    public OrderReplyListener(ObjectMapper objectMapper, OrderSagaOrchestrator orchestrator) {
        this.objectMapper = objectMapper;
        this.orchestrator = orchestrator;
    }

    @KafkaListener(topics = Topics.ORDER_REPLIES, groupId = "order-service")
    public void onMessage(String json) throws JsonProcessingException {
        orchestrator.handle(objectMapper.readValue(json, OrderReply.class));
    }
}
