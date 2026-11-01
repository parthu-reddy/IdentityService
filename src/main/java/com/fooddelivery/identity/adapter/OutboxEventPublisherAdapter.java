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
            NotificationRequestEvent event = NotificationRequestEvent.builder().explicitRecipient(recipientPhoneNumber).channel(com.fooddelivery.common.enums.ChannelType.valueOf(channelType)).eventName(com.fooddelivery.common.constants.NotificationTemplate.OTP_LOGIN).templateParams(java.util.List.of(otp)).payload(java.util.Map.of("otp", otp)).build();
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType(com.fooddelivery.common.constants.AggregateType.NOTIFICATION)
                .aggregateId(event.getEventId())
                .eventType(com.fooddelivery.common.constants.EventType.NOTIFICATION_REQUEST)
                .payload(objectMapper.writeValueAsString(event))
                .createdAt(java.time.Instant.now())
                .status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED)
                .build();
            outboxEventRepository.save(outboxEvent);
            log.info("Saved signup notification to outbox eventId={}", event.getEventId());
        } catch (Exception e) {
            log.error("Failed to serialize NotificationRequestEvent for outbox", e);
            throw new RuntimeException("Failed to publish notification event via Outbox", e);
        }
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void publishOrganisationInvitation(UUID invitationId, String phoneNumber, UUID existingUserId,
                                              com.fooddelivery.common.enums.OrganisationRole role) {
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .eventId("organisation-invitation:" + invitationId)
                .userId(existingUserId)
                .explicitRecipient(phoneNumber)
                .channel(com.fooddelivery.common.enums.ChannelType.SMS)
                .eventName(com.fooddelivery.common.constants.NotificationTemplate.ORGANISATION_INVITATION)
                .templateParams(java.util.List.of(roleLabel(role)))
                .payload(java.util.Map.of("invitationId", invitationId.toString(), "role", role.name()))
                .build();
        try {
            outboxEventRepository.save(OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(com.fooddelivery.common.constants.AggregateType.NOTIFICATION)
                    .aggregateId(invitationId.toString())
                    .eventType(com.fooddelivery.common.constants.EventType.NOTIFICATION_REQUEST)
                    .idempotencyKey(event.getEventId())
                    .payload(objectMapper.writeValueAsString(event))
                    .createdAt(java.time.Instant.now())
                    .status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED)
                    .build());
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException("Invitation notification could not be stored", failure);
        }
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void publishDineInSeated(UUID sessionId, UUID guestUserId, String phoneNumber) {
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .eventId("dine-in-seated:" + sessionId + ":" + guestUserId)
                .explicitRecipient(phoneNumber)
                .channel(com.fooddelivery.common.enums.ChannelType.SMS)
                .eventName(com.fooddelivery.common.constants.NotificationTemplate.DINE_IN_SEATED)
                .templateParams(java.util.List.of())
                .build();
        // The outbox idempotency key is unique: the same guest added to the same table again is told once.
        if (outboxEventRepository.findByIdempotencyKey(event.getEventId()).isPresent()) {
            return;
        }
        try {
            outboxEventRepository.save(OutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(com.fooddelivery.common.constants.AggregateType.NOTIFICATION)
                    .aggregateId(sessionId.toString())
                    .eventType(com.fooddelivery.common.constants.EventType.NOTIFICATION_REQUEST)
                    .idempotencyKey(event.getEventId())
                    .payload(objectMapper.writeValueAsString(event))
                    .createdAt(java.time.Instant.now())
                    .status(com.fooddelivery.common.enums.OutboxStatus.UNPROCESSED)
                    .build());
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException("Dine-in seated notification could not be stored", failure);
        }
    }

    static String roleLabel(com.fooddelivery.common.enums.OrganisationRole role) {
        return switch (role) {
            case OWNER -> "an owner";
            case ADMIN -> "an admin";
            case MANAGER -> "a manager";
            case STAFF -> "a staff member";
        };
    }

    @java.lang.SuppressWarnings("all")
    public OutboxEventPublisherAdapter(final OutboxEventRepository outboxEventRepository, final ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }
}
