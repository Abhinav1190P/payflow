package com.payflow.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.payflow.common.events.OrderEvent;
import com.payflow.support.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class NotificationServiceTest {

    @Autowired
    private NotificationService service;
    @Autowired
    private NotificationRepository notifications;

    @Test
    void duplicateEventNotifiesOnce() {
        UUID user = UUID.randomUUID();
        var event = new OrderEvent.OrderConfirmed(UUID.randomUUID().toString(), UUID.randomUUID(), user, 150_000);
        service.handle(event);
        service.handle(event);
        var mine = notifications.findByUserId(user, Pageable.unpaged()).getContent();
        assertThat(mine).singleElement().satisfies(n -> assertThat(n.getMessage()).contains("confirmed").contains("1500.0"));
    }
}
