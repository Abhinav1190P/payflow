package com.payflow.notification;

import com.payflow.common.events.OrderEvent;
import com.payflow.messaging.inbox.Inbox;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    static final String CONSUMER = "notification";

    private final NotificationRepository notifications;
    private final List<NotificationChannel> channels;
    private final Inbox inbox;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, List<NotificationChannel> channels, Inbox inbox, Clock clock) {
        this.notifications = notifications;
        this.channels = channels;
        this.inbox = inbox;
        this.clock = clock;
    }

    // Idempotent: a redelivered event finds its id in the inbox and does not notify the user twice.
    @Transactional
    public void handle(OrderEvent event) {
        if (!inbox.firstTime(CONSUMER, event.messageId())) {
            return;
        }
        String message = switch (event) {
            case OrderEvent.OrderConfirmed c -> "Your order " + c.orderId() + " is confirmed. Total: " + c.totalMinor() / 100.0;
            case OrderEvent.OrderCancelled c -> "Your order " + c.orderId() + " was cancelled: " + c.reason();
        };
        Notification saved = notifications.save(new Notification(event.userId(), event.orderId(),
                event.getClass().getSimpleName(), message, clock.instant()));
        channels.forEach(channel -> channel.send(saved));
    }
}
