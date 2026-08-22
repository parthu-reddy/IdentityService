package com.fooddelivery.identity.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.common.constants.AppConstants;
import com.fooddelivery.identity.port.EventPublisherPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
@lombok.extern.slf4j.Slf4j
public class OutboxEventPublisherAdapter implements EventPublisherPort {
    @java.lang.SuppressWarnings("all")

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void publishNotificationEvent(String recipientPhoneNumber, String channelType, String otp) {
        try {
            NotificationRequestEvent event = NotificationRequestEvent.builder().explicitRecipient(recipientPhoneNumber).channel(com.fooddelivery.common.enums.ChannelType.valueOf(channelType)).eventName("OTP_LOGIN").payload(java.util.Map.of("otp", otp)).build();
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType(com.fooddelivery.common.constants.AggregateType.NOTIFICATION)
                .aggregateId(recipientPhoneNumber)
                .eventType(com.fooddelivery.common.constants.EventType.NOTIFICATION_REQUEST)
                .payload(objectMapper.writeValueAsString(event))
                .createdAt(java.time.LocalDateTime.now())
                .status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED)
                .build();
            outboxEventRepository.save(outboxEvent);
            log.info("Saved NotificationRequestEvent to Outbox for phone: {}", recipientPhoneNumber);
        } catch (Exception e) {
            log.error("Failed to serialize NotificationRequestEvent for outbox", e);
            throw new RuntimeException("Failed to publish notification event via Outbox", e);
        }
    }

    @java.lang.SuppressWarnings("all")
    public OutboxEventPublisherAdapter(final OutboxEventRepository outboxEventRepository, final ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }
}
