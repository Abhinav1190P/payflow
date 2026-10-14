package com.payflow.notification;

// Strategy for HOW a user is told (email, SMS, push). Only a logging channel exists here; a real email
// provider would be one more implementation, with no change to NotificationService.
public interface NotificationChannel {

    void send(Notification notification);
}
