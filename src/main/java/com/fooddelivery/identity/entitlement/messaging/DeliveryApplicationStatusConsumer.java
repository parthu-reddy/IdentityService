package com.fooddelivery.identity.entitlement.messaging;

import com.fooddelivery.common.constants.*;
import com.fooddelivery.common.event.EventBindingException;
import com.fooddelivery.common.event.application.DeliveryApplicationStatusChangedEvent;
import com.fooddelivery.identity.entitlement.service.*;
import org.springframework.kafka.annotation.*;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service @lombok.RequiredArgsConstructor @lombok.extern.slf4j.Slf4j
public class DeliveryApplicationStatusConsumer {
    private final EntitlementEventProcessor processor;
    private final EntitlementProjectionService projections;
    @KafkaListener(topics=KafkaConstants.TOPIC_DELIVERY_PARTNER_EVENTS,groupId="identity-service-group-delivery-applications")
    @RetryableTopic(attempts="5",backoff=@Backoff(delay=1000,multiplier=2),exclude=EventBindingException.class,traversingCauses="true")
    public void consume(String message,@Headers Map<String,Object> headers) {
        processor.process(EventType.DELIVERY_APPLICATION_STATUS_CHANGED,DeliveryApplicationStatusChangedEvent.class,message,headers,projections::delivery);
    }
    @DltHandler public void deadLetter(String ignored,@Headers Map<String,Object> headers) {
        log.error("Delivery entitlement event exhausted retries replay={}",com.fooddelivery.common.util.KafkaHeaderUtils.deadLetterPosition(headers));
    }
}
