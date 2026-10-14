package com.payflow.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(LoggingChannel.class);

    @Override
    public void send(Notification notification) {
        log.info("notify user {}: {}", notification.getUserId(), notification.getMessage());
    }
}
