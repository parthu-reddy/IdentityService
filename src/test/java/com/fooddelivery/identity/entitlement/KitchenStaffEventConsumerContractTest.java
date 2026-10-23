package com.fooddelivery.identity.entitlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.enums.*;
import com.fooddelivery.common.event.EventBinder;
import com.fooddelivery.common.event.KitchenStaffChangedEvent;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import com.fooddelivery.identity.entitlement.messaging.KitchenStaffEventConsumer;
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

/** The current restaurant-service kitchen_staff_changed stub feeds the real typed listener in memory; no database or broker fixture. */
@SpringBootTest(classes=KitchenStaffEventConsumerContractTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
    properties={"spring.cloud.config.enabled=false","eureka.client.enabled=false",
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"})
@AutoConfigureStubRunner(ids={"com.fooddelivery:restaurant-application:1.0-SNAPSHOT:stubs"},
    stubsMode=org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties.StubsMode.LOCAL)
class KitchenStaffEventConsumerContractTest {
    @Configuration @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @Import({EntitlementEventProcessor.class,KitchenStaffEventConsumer.class})
    static class Config {
        @Bean EventBinder binder(ObjectMapper mapper) {return new EventBinder(mapper,jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());}
        @Bean PlatformTransactionManager transactions() {var manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());return manager;}
        @Bean MessageVerifierSender<Message<?>> sender(ObjectMapper mapper,KitchenStaffEventConsumer kitchen) {
            return new MessageVerifierSender<>() {
                @Override public void send(Message<?> message,String destination,YamlContract contract) {send(message.getPayload(),message.getHeaders(),destination,contract);}
                @Override public <T> void send(T payload,Map<String,Object> headers,String destination,YamlContract contract) {
                    try {
                        String json=payload instanceof String?(String)payload:mapper.writeValueAsString(payload);
                        if(destination.equals("kitchen-events"))kitchen.consume(json,headers);
                        else throw new IllegalArgumentException("Unexpected producer destination "+destination);
                    } catch(com.fasterxml.jackson.core.JsonProcessingException error) {throw new IllegalStateException(error);}
                }
            };
        }
    }
    @MockBean EntitlementProjectionService projections; @MockBean IIdempotencyKeyRepository claims;
    @Autowired StubTrigger trigger;
    @Test void kitchenStaffProducerRevisionBindsEveryFieldAndIncludesItsIdempotencyId() {
        when(claims.tryClaim(anyString())).thenReturn(1);trigger.trigger("kitchen_staff_changed");
        var event=org.mockito.ArgumentCaptor.forClass(KitchenStaffChangedEvent.class);
        verify(projections).kitchen(event.capture());var e=event.getValue();
        assertEquals(UUID.fromString("7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"),e.getStaffId());
        assertEquals(UUID.fromString("321e4567-e89b-12d3-a456-426614174000"),e.getOrganisationId());
        assertEquals(UUID.fromString("9c8b7a65-1e2d-4f30-b5a6-7c8d9e0f1a23"),e.getOutletId());
        assertEquals("8000000505",e.getPhoneNumber());
        assertEquals(UUID.fromString("6b1d3c22-9f45-4a7e-8c11-2d4e6f8a9b02"),e.getUserId());
        assertEquals(Set.of(KitchenRole.CHEF),e.getRoles());assertEquals(KitchenStaffStatus.ACTIVE,e.getStatus());
        assertEquals(2,e.getStaffVersion());assertEquals(java.time.Instant.parse("2026-10-20T12:00:00Z"),e.getOccurredAt());
        verify(claims).tryClaim("processed_event:identity:5d1c2b3a-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    }
}
