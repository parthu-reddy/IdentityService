package com.fooddelivery.identity.entitlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.event.EventBinder;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import com.fooddelivery.identity.entitlement.messaging.*;
import com.fooddelivery.identity.entitlement.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.stubrunner.*;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.verifier.converter.YamlContract;
import org.springframework.cloud.contract.verifier.messaging.MessageVerifierSender;
import org.springframework.context.annotation.*;
import org.springframework.messaging.Message;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Current producer stubs feed actual typed listeners in memory; no database or broker fixture. */
@SpringBootTest(classes=EntitlementProjectionConsumerContractTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
    properties={"spring.cloud.config.enabled=false","eureka.client.enabled=false",
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"})
@AutoConfigureStubRunner(ids={"com.fooddelivery:restaurant-application:1.0-SNAPSHOT:stubs","com.fooddelivery:delivery-executive-application:1.0-SNAPSHOT:stubs"},
    stubsMode=org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties.StubsMode.LOCAL)
class EntitlementProjectionConsumerContractTest {
    @Configuration @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @Import({EntitlementEventProcessor.class,RestaurantApplicationStatusConsumer.class,DeliveryApplicationStatusConsumer.class})
    static class Config {
        @Bean EventBinder binder(ObjectMapper mapper) {return new EventBinder(mapper,jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());}
        @Bean PlatformTransactionManager transactions() {var manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());return manager;}
        @Bean MessageVerifierSender<Message<?>> sender(ObjectMapper mapper,RestaurantApplicationStatusConsumer restaurant,DeliveryApplicationStatusConsumer delivery) {
            return new MessageVerifierSender<>() {
                @Override public void send(Message<?> message,String destination,YamlContract contract) {send(message.getPayload(),message.getHeaders(),destination,contract);}
                @Override public <T> void send(T payload,Map<String,Object> headers,String destination,YamlContract contract) {
                    try {
                        String json=payload instanceof String?(String)payload:mapper.writeValueAsString(payload);
                        if(destination.equals("restaurant-events"))restaurant.consume(json,headers);
                        else if(destination.equals("delivery-partner-events"))delivery.consume(json,headers);
                        else throw new IllegalArgumentException("Unexpected producer destination");
                    } catch(com.fasterxml.jackson.core.JsonProcessingException error) {throw new IllegalStateException(error);}
                }
            };
        }
    }
    @MockBean EntitlementProjectionService projections; @MockBean IIdempotencyKeyRepository claims;
    @Autowired StubTrigger trigger;
    @Test void restaurantProducerRevisionBindsAndIncludesItsIdempotencyId() {
        when(claims.tryClaim(anyString())).thenReturn(1);trigger.trigger("restaurant_application_status_changed");
        var event=org.mockito.ArgumentCaptor.forClass(com.fooddelivery.common.event.application.RestaurantApplicationStatusChangedEvent.class);
        verify(projections).restaurant(event.capture());assertEquals(UUID.fromString("9c8b7a65-1e2d-4f30-b5a6-7c8d9e0f1a23"),event.getValue().brandId());
        assertEquals(UUID.fromString("321e4567-e89b-12d3-a456-426614174000"),event.getValue().organisationId());
        assertEquals(3,event.getValue().applicationVersion());assertEquals(com.fooddelivery.common.enums.ApplicationStatus.SUBMITTED,event.getValue().status());
        assertEquals(java.time.Instant.parse("2026-10-04T00:00:00Z"),event.getValue().changedAt());
        verify(claims).tryClaim("processed_event:identity:3f2504e0-4f89-41d3-9a0c-0305e82c3301");
    }
    @Test void deliveryProducerRevisionBindsAndIncludesItsIdempotencyId() {
        when(claims.tryClaim(anyString())).thenReturn(1);trigger.trigger("delivery_application_status_changed");
        var event=org.mockito.ArgumentCaptor.forClass(com.fooddelivery.common.event.application.DeliveryApplicationStatusChangedEvent.class);
        verify(projections).delivery(event.capture());assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),event.getValue().executiveId());
        assertEquals(3,event.getValue().applicationVersion());assertEquals(com.fooddelivery.common.enums.ApplicationStatus.SUBMITTED,event.getValue().status());
        assertEquals(java.time.Instant.parse("2026-10-04T00:00:00Z"),event.getValue().changedAt());
        verify(claims).tryClaim("processed_event:identity:0f1a5cb3-2b6d-5a1e-9c47-8e3f6d2a1b04");
    }
}
