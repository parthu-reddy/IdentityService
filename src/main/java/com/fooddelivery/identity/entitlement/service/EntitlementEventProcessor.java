package com.fooddelivery.identity.entitlement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.event.EventBinder;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import com.fooddelivery.common.util.KafkaHeaderUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;
import java.util.function.Consumer;

@Service @lombok.extern.slf4j.Slf4j
public class EntitlementEventProcessor {
    private final ObjectMapper mapper;
    private final EventBinder binder;
    private final IIdempotencyKeyRepository idempotency;
    private final TransactionTemplate transaction;

    public EntitlementEventProcessor(ObjectMapper mapper,EventBinder binder,IIdempotencyKeyRepository idempotency,
            org.springframework.transaction.PlatformTransactionManager manager) {
        this.mapper=mapper;this.binder=binder;this.idempotency=idempotency;this.transaction=new TransactionTemplate(manager);
    }

    public <T> void process(EventType expected,Class<T> type,String message,Map<String,Object> headers,Consumer<T> apply) {
        final String eventType;
        try {
            var body=mapper.readTree(message);
            eventType=body!=null && body.hasNonNull("eventType")?body.path("eventType").asText():KafkaHeaderUtils.extractEventType(headers,body);
        }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalArgumentException("Invalid entitlement event envelope",ex); }
        if (!expected.name().equals(eventType)) { log.debug("Ignoring unrelated entitlement topic event type={}",eventType);return; }
        var event=binder.bindIf(expected,eventType,message,type).orElseThrow();
        String id=KafkaHeaderUtils.extractHeaderValue(headers,"eventId");
        if (id==null || id.isBlank()) throw new IllegalArgumentException("Entitlement event requires an eventId");
        transaction.executeWithoutResult(status -> {
            if (idempotency.tryClaim("processed_event:identity:"+id)==0) return;
            apply.accept(event);
        });
    }
}
