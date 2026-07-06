package com.fooddelivery.identity.port;

public interface EventPublisherPort {
    void publishNotificationEvent(String recipientPhoneNumber, String channelType, String otp);
}
