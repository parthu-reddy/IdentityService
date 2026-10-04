package com.fooddelivery.identity.entitlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.*;
import com.fooddelivery.common.enums.*;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import com.fooddelivery.identity.entitlement.messaging.*;
import com.fooddelivery.identity.entitlement.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual listener -> typed binder -> native claim -> real H2 projection transaction. */
@Transactional(propagation=Propagation.NOT_SUPPORTED)
// These transactions deliberately commit. Do not share their data with rollback-only role tests.
@TestPropertySource(properties="spring.datasource.url=jdbc:h2:mem:o4_projection_consumer;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class EntitlementProjectionConsumerTest extends EntitlementH2Fixture {
    @Autowired EntitlementEventProcessor processor; @Autowired EntitlementProjectionService projection;
    @Autowired IIdempotencyKeyRepository claims; @Autowired PlatformTransactionManager manager; @Autowired ObjectMapper mapper;
    RestaurantApplicationStatusConsumer restaurant(){return new RestaurantApplicationStatusConsumer(processor,projection);}
    DeliveryApplicationStatusConsumer rider(){return new DeliveryApplicationStatusConsumer(processor,projection);}
    Map<String,Object> headers(UUID event,EventType type) {return Map.of("eventId",event.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),"eventType",type.name());}
    String message(com.fooddelivery.identity.organisation.entity.Organisation org,UUID brand,ApplicationStatus status,long version) throws Exception {
        return mapper.writeValueAsString(Map.of("brandId",brand,"organisationId",org.getId(),"status",status,"applicationVersion",version,"changedAt",now));
    }
    @Test void duplicateIdAndOlderVersionCannotRegressApprovalOrPublishAgain() throws Exception {
        var person=person();var org=organisation(person,OrganisationStatus.ACTIVE,MembershipStatus.ACTIVE);var brand=UUID.randomUUID();var id=UUID.randomUUID();
        var headers=headers(id,EventType.RESTAURANT_APPLICATION_STATUS_CHANGED);
        restaurant().consume(message(org,brand,ApplicationStatus.APPROVED,4),headers);
        restaurant().consume(message(org,brand,ApplicationStatus.SUSPENDED,5),headers);
        restaurant().consume(message(org,brand,ApplicationStatus.REJECTED,3),headers(UUID.randomUUID(),EventType.RESTAURANT_APPLICATION_STATUS_CHANGED));
        assertTrue(claims.existsById("processed_event:identity:"+id));assertEquals(1,users.findById(person.getId()).orElseThrow().getEntitlementsVersion());
        assertTrue(entitlements.rolesFor(person.getId()).contains(RoleName.RESTAURANT));verify(cache,times(1)).putVersions(anyMap(),any());
    }
    @Test void failedTransactionRollsBackClaimProjectionAndCounterAndCanReplay() throws Exception {
        var person=person();var org=organisation(person,OrganisationStatus.ACTIVE,MembershipStatus.ACTIVE);var brand=UUID.randomUUID();var id=UUID.randomUUID();var json=message(org,brand,ApplicationStatus.APPROVED,1);var headers=headers(id,EventType.RESTAURANT_APPLICATION_STATUS_CHANGED);
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(manager).execute(status->{restaurant().consume(json,headers);throw new IllegalStateException("Abort");}));
        assertFalse(claims.existsById("processed_event:identity:"+id));assertEquals(0,users.findById(person.getId()).orElseThrow().getEntitlementsVersion());verifyNoInteractions(cache);
        restaurant().consume(json,headers);assertTrue(entitlements.rolesFor(person.getId()).contains(RoleName.RESTAURANT));verify(cache).putVersions(anyMap(),any());
    }
    @Test void bodyTypeWinsAndUnrelatedOrMalformedEventsCannotClaimOrGrant() throws Exception {
        var person=person();var id=UUID.randomUUID();
        var unrelated=mapper.writeValueAsString(Map.of("eventType","BRAND_CREATED"));
        restaurant().consume(unrelated,headers(id,EventType.RESTAURANT_APPLICATION_STATUS_CHANGED));
        assertFalse(claims.existsById("processed_event:identity:"+id));
        assertThrows(com.fooddelivery.common.event.EventBindingException.class,()->rider().consume("{\"executiveId\":null}",headers(id,EventType.DELIVERY_APPLICATION_STATUS_CHANGED)));
        assertFalse(claims.existsById("processed_event:identity:"+id));assertEquals(0,users.findById(person.getId()).orElseThrow().getEntitlementsVersion());verifyNoInteractions(cache);
    }
    @Test void missingEventIdRefusesBeforeApplyingDeliveryAndValidEventIsIdempotent() throws Exception {
        var person=person();var json=mapper.writeValueAsString(Map.of("executiveId",person.getId(),"status",ApplicationStatus.APPROVED,"applicationVersion",1,"changedAt",now));
        assertThrows(IllegalArgumentException.class,()->rider().consume(json,Map.of("eventType",EventType.DELIVERY_APPLICATION_STATUS_CHANGED.name())));
        var headers=headers(UUID.randomUUID(),EventType.DELIVERY_APPLICATION_STATUS_CHANGED);rider().consume(json,headers);rider().consume(json,headers);
        assertEquals(List.of(RoleName.CUSTOMER,RoleName.DELIVERY),entitlements.rolesFor(person.getId()));verify(cache,times(1)).putVersions(anyMap(),any());
    }
}
