package com.fooddelivery.identity.entitlement.messaging;

import com.fooddelivery.common.constants.*;
import com.fooddelivery.common.event.EventBindingException;
import com.fooddelivery.common.event.application.RestaurantApplicationStatusChangedEvent;
import com.fooddelivery.identity.entitlement.service.*;
import org.springframework.kafka.annotation.*;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service @lombok.RequiredArgsConstructor @lombok.extern.slf4j.Slf4j
public class RestaurantApplicationStatusConsumer {
    private final EntitlementEventProcessor processor;
    private final EntitlementProjectionService projections;
    @KafkaListener(topics=KafkaConstants.TOPIC_RESTAURANT_EVENTS,groupId="identity-service-group-restaurant-applications")
    @RetryableTopic(attempts="5",backoff=@Backoff(delay=1000,multiplier=2),exclude=EventBindingException.class,traversingCauses="true")
    public void consume(String message,@Headers Map<String,Object> headers) {
        processor.process(EventType.RESTAURANT_APPLICATION_STATUS_CHANGED,RestaurantApplicationStatusChangedEvent.class,message,headers,projections::restaurant);
    }
    @DltHandler public void deadLetter(String ignored,@Headers Map<String,Object> headers) {
        log.error("Restaurant entitlement event exhausted retries replay={}",com.fooddelivery.common.util.KafkaHeaderUtils.deadLetterPosition(headers));
    }
}
