package com.fooddelivery.identity.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxEventPublisherAdapterTest {
    @Test void signupNotificationKeepsPhoneOutOfThePublishedAggregateIdentityAndLogs() throws Exception {
        var repository = mock(OutboxEventRepository.class);
        var mapper = new ObjectMapper();
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(OutboxEventPublisherAdapter.class);
        var logs = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        logs.start(); logger.addAppender(logs);
        try {
            new OutboxEventPublisherAdapter(repository, mapper).publishNotificationEvent("8999123456", "SMS", "123456");
            var capture = org.mockito.ArgumentCaptor.forClass(OutboxEventEntity.class);
            verify(repository).save(capture.capture());
            var event = capture.getValue(); var body = mapper.readTree(event.getPayload());
            assertEquals(body.get("eventId").asText(), event.getAggregateId());
            assertNull(body.get("userId").textValue());
            assertEquals("8999123456", body.get("explicitRecipient").asText());
            assertEquals("OTP_LOGIN", body.get("eventName").asText());
            assertTrue(logs.list.stream().noneMatch(log -> log.getFormattedMessage().contains("8999123456")
                    || log.getFormattedMessage().contains("123456")));
        } finally { logger.detachAppender(logs); logs.stop(); }
    }

    @Test void dineInConsentSmsCarriesOnlyTheCodeUnderItsOwnTemplate() throws Exception {
        var repository = mock(OutboxEventRepository.class);
        var mapper = new ObjectMapper();
        var consentId = java.util.UUID.randomUUID();
        new OutboxEventPublisherAdapter(repository, mapper).publishDineInConsentCode(consentId, "8999123456", "482913");
        var capture = org.mockito.ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repository).save(capture.capture());
        var event = capture.getValue(); var body = mapper.readTree(event.getPayload());
        assertEquals(consentId.toString(), event.getAggregateId());
        assertEquals("dine-in-consent:" + consentId, event.getIdempotencyKey());
        assertEquals("DINE_IN_CONSENT", body.get("eventName").asText());
        assertEquals("SMS", body.get("channel").asText());
        assertEquals("8999123456", body.get("explicitRecipient").asText());
        assertNull(body.get("userId").textValue());
        assertEquals(java.util.List.of("482913"), mapper.convertValue(body.get("templateParams"), java.util.List.class));
    }
}
