package com.payflow.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.events.PaymentCommand;
import com.payflow.common.events.Topics;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Kafka consumer for saga commands. Unknown/broken JSON throws, so Kafka's error handler retries and then
// moves the record to payment.commands.DLT. Technical database blips go to the retry scheduler instead.
@Component
public class PaymentCommandListener {

    private final ObjectMapper objectMapper;
    private final PaymentRetryScheduler scheduler;

    public PaymentCommandListener(ObjectMapper objectMapper, PaymentRetryScheduler scheduler) {
        this.objectMapper = objectMapper;
        this.scheduler = scheduler;
    }

    @KafkaListener(topics = Topics.PAYMENT_COMMANDS, groupId = "payment-service")
    public void onMessage(String json) throws JsonProcessingException {
        PaymentCommand command = objectMapper.readValue(json, PaymentCommand.class);
        try {
            scheduler.dispatch(command);
        } catch (TransientDataAccessException e) {
            scheduler.schedule(command, 1);
        }
    }
}
