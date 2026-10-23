package com.fooddelivery.identity.entitlement.messaging;

import com.fooddelivery.common.constants.*;
import com.fooddelivery.common.event.EventBindingException;
import com.fooddelivery.common.event.KitchenStaffChangedEvent;
import com.fooddelivery.identity.entitlement.service.*;
import org.springframework.kafka.annotation.*;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service @lombok.RequiredArgsConstructor @lombok.extern.slf4j.Slf4j
public class KitchenStaffEventConsumer {
    private final EntitlementEventProcessor processor;
    private final EntitlementProjectionService projections;
    @KafkaListener(topics=KafkaConstants.TOPIC_KITCHEN_EVENTS,groupId="identity-service-group-kitchen-staff")
    @RetryableTopic(attempts="5",backoff=@Backoff(delay=1000,multiplier=2),exclude=EventBindingException.class,traversingCauses="true")
    public void consume(String message,@Headers Map<String,Object> headers) {
        processor.process(EventType.KITCHEN_STAFF_CHANGED,KitchenStaffChangedEvent.class,message,headers,projections::kitchen);
    }
    @DltHandler public void deadLetter(String ignored,@Headers Map<String,Object> headers) {
        log.error("Kitchen entitlement event exhausted retries replay={}",com.fooddelivery.common.util.KafkaHeaderUtils.deadLetterPosition(headers));
    }
}
