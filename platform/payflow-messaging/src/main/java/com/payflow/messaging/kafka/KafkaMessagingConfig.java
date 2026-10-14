package com.payflow.messaging.kafka;

import com.payflow.common.events.Topics;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaMessagingConfig {

    // Concept: dead-letter topic. A message that keeps failing (bad data, a bug) is retried twice, one second
    // apart, then moved to "<topic>.DLT" so it stops blocking the partition and can be inspected and replayed.
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + Topics.DLT_SUFFIX, record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));
    }

    @Bean
    public KafkaAdmin.NewTopics payflowTopics() {
        return new KafkaAdmin.NewTopics(
                topic(Topics.PAYMENT_COMMANDS), topic(Topics.PAYMENT_COMMANDS + Topics.DLT_SUFFIX),
                topic(Topics.INVENTORY_COMMANDS), topic(Topics.INVENTORY_COMMANDS + Topics.DLT_SUFFIX),
                topic(Topics.ORDER_REPLIES), topic(Topics.ORDER_REPLIES + Topics.DLT_SUFFIX),
                topic(Topics.ORDER_EVENTS), topic(Topics.ORDER_EVENTS + Topics.DLT_SUFFIX));
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
