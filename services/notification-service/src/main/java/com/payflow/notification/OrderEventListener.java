package com.payflow.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.events.OrderEvent;
import com.payflow.common.events.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private final ObjectMapper objectMapper;
    private final NotificationService notifications;

    public OrderEventListener(ObjectMapper objectMapper, NotificationService notifications) {
        this.objectMapper = objectMapper;
        this.notifications = notifications;
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "notification-service")
    public void onMessage(String json) throws JsonProcessingException {
        notifications.handle(objectMapper.readValue(json, OrderEvent.class));
    }
}
