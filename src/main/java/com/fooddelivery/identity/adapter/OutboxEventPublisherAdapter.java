package com.fooddelivery.identity.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.common.constants.AppConstants;
import com.fooddelivery.identity.port.EventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisherAdapter implements EventPublisherPort {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void publishNotificationEvent(String recipientPhoneNumber, String channelType) {
        try {
            NotificationRequestEvent event = NotificationRequestEvent.builder()
                    .explicitRecipient(recipientPhoneNumber)
                    .channel(com.fooddelivery.common.enums.ChannelType.valueOf(channelType))
                    .build();

            OutboxEventEntity outboxEvent = new OutboxEventEntity();
            outboxEvent.setId(UUID.randomUUID());
            outboxEvent.setAggregateType(AppConstants.AGGREGATE_NOTIFICATION);
            outboxEvent.setAggregateId(recipientPhoneNumber); // Routing key
            outboxEvent.setEventType("NOTIFICATION_REQUEST");
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
            outboxEvent.setCreatedAt(java.time.LocalDateTime.now());
            
            outboxEventRepository.save(outboxEvent);
            log.info("Saved NotificationRequestEvent to Outbox for phone: {}", recipientPhoneNumber);
        } catch (Exception e) {
            log.error("Failed to serialize NotificationRequestEvent for outbox", e);
            throw new RuntimeException("Failed to publish notification event via Outbox", e);
        }
    }
}
