package com.fooddelivery.identity.entitlement;

import com.fooddelivery.common.enums.*;
import com.fooddelivery.common.event.KitchenStaffChangedEvent;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.entitlement.repository.KitchenEntitlementRepository;
import com.fooddelivery.identity.entitlement.service.EntitlementProjectionService;
import com.fooddelivery.identity.organisation.entity.Organisation;
import com.fooddelivery.identity.organisation.repository.OrganisationRepository;
import com.fooddelivery.identity.port.CachePort;
import com.fooddelivery.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Embedded persistence proof of the kitchen-events projection; no external database, Redis, Kafka or Docker fixture. */
@DataJpaTest(properties={"spring.cloud.config.enabled=false","spring.flyway.enabled=false",
    "spring.cloud.discovery.enabled=false","eureka.client.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:kitchen_entitlements;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=EntitlementJpaTestConfiguration.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class KitchenEntitlementProjectionTest {
    @Autowired EntitlementProjectionService projection;
    @Autowired KitchenEntitlementRepository kitchens;
    @Autowired UserRepository users;
    @Autowired OrganisationRepository organisations;
    @MockBean CachePort cache;
    static final java.util.concurrent.atomic.AtomicInteger phones=new java.util.concurrent.atomic.AtomicInteger();
    final Instant now=Instant.parse("2026-10-03T10:00:00Z");

    AppUser person() {
        return users.saveAndFlush(AppUser.builder().phoneNumber("77770"+String.format("%05d",phones.incrementAndGet())).isActive(true).build());
    }
    UUID organisation(AppUser creator) {
        return organisations.saveAndFlush(Organisation.builder().id(UUID.randomUUID()).displayName("Kitchen test organisation")
                .status(OrganisationStatus.ACTIVE).createdBy(creator.getId()).createdAt(now).updatedAt(now).build()).getId();
    }
    KitchenStaffChangedEvent event(UUID staffId,UUID orgId,String phone,UUID userId,KitchenStaffStatus status,long version,KitchenRole... roles) {
        return KitchenStaffChangedEvent.builder().staffId(staffId).organisationId(orgId).outletId(UUID.randomUUID()).phoneNumber(phone)
                .userId(userId).roles(EnumSet.copyOf(List.of(roles))).status(status).staffVersion(version).occurredAt(now).build();
    }
    long version(AppUser u){return users.findById(u.getId()).orElseThrow().getEntitlementsVersion();}

    @Test void anInvitationIsStoredWithItsRoleFlagsAndBumpsWhoeverSignsInWithThePhone() {
        var owner=person();var phoneOwner=person();var org=organisation(owner);var staffId=UUID.randomUUID();
        assertTrue(projection.kitchen(event(staffId,org,phoneOwner.getPhoneNumber(),null,KitchenStaffStatus.INVITED,1,KitchenRole.MANAGER,KitchenRole.CHEF)));
        var row=kitchens.findById(staffId).orElseThrow();
        assertEquals(org,row.getOrganisationId());assertEquals(phoneOwner.getPhoneNumber(),row.getPhoneNumber());assertNull(row.getUserId());
        assertTrue(row.isManager());assertTrue(row.isChef());assertEquals(KitchenStaffStatus.INVITED,row.getStatus());assertEquals(1,row.getStaffVersion());
        assertEquals(now,row.getUpdatedAt());
        assertEquals(1,version(phoneOwner));assertEquals(0,version(owner));
    }
    @Test void duplicateAndOlderStaffVersionsAreIgnoredAndDoNotBumpAgain() {
        var owner=person();var cook=person();var org=organisation(owner);var staffId=UUID.randomUUID();
        assertTrue(projection.kitchen(event(staffId,org,cook.getPhoneNumber(),cook.getId(),KitchenStaffStatus.ACTIVE,3,KitchenRole.CHEF)));
        assertFalse(projection.kitchen(event(staffId,org,cook.getPhoneNumber(),cook.getId(),KitchenStaffStatus.REMOVED,2,KitchenRole.CHEF)));
        assertFalse(projection.kitchen(event(staffId,org,cook.getPhoneNumber(),cook.getId(),KitchenStaffStatus.REMOVED,3,KitchenRole.CHEF)));
        assertEquals(KitchenStaffStatus.ACTIVE,kitchens.findById(staffId).orElseThrow().getStatus());
        assertEquals(1,version(cook));
    }
    @Test void aNewerRemovalIsStoredAndBumpsTheBoundPerson() {
        var owner=person();var cook=person();var org=organisation(owner);var staffId=UUID.randomUUID();
        projection.kitchen(event(staffId,org,cook.getPhoneNumber(),cook.getId(),KitchenStaffStatus.ACTIVE,1,KitchenRole.CHEF));
        assertTrue(projection.kitchen(event(staffId,org,cook.getPhoneNumber(),cook.getId(),KitchenStaffStatus.REMOVED,2,KitchenRole.CHEF)));
        var row=kitchens.findById(staffId).orElseThrow();
        assertEquals(KitchenStaffStatus.REMOVED,row.getStatus());assertFalse(row.isManager());assertTrue(row.isChef());
        assertEquals(2,version(cook));
    }
    @Test void rebindingBumpsThePhoneOwnerThePreviouslyBoundAndTheNewlyBoundPerson() {
        var owner=person();var phoneOwner=person();var before=person();var after=person();var org=organisation(owner);var staffId=UUID.randomUUID();
        projection.kitchen(event(staffId,org,phoneOwner.getPhoneNumber(),before.getId(),KitchenStaffStatus.ACTIVE,1,KitchenRole.CHEF));
        assertEquals(1,version(phoneOwner));assertEquals(1,version(before));assertEquals(0,version(after));
        projection.kitchen(event(staffId,org,phoneOwner.getPhoneNumber(),after.getId(),KitchenStaffStatus.ACTIVE,2,KitchenRole.CHEF));
        assertEquals(2,version(phoneOwner));assertEquals(2,version(before));assertEquals(1,version(after));
        assertEquals(after.getId(),kitchens.findById(staffId).orElseThrow().getUserId());
    }
    @Test void anUnknownOrganisationThrowsSoTheEventIsRetriedAndNothingIsStored() {
        var cook=person();var staffId=UUID.randomUUID();
        assertThrows(IllegalStateException.class,
                () -> projection.kitchen(event(staffId,UUID.randomUUID(),cook.getPhoneNumber(),null,KitchenStaffStatus.INVITED,1,KitchenRole.CHEF)));
        assertFalse(kitchens.existsById(staffId));assertEquals(0,version(cook));
    }
    @Test void aStaffRowCannotMoveToAnotherOrganisation() {
        var owner=person();var cook=person();var org=organisation(owner);var other=organisation(owner);var staffId=UUID.randomUUID();
        projection.kitchen(event(staffId,org,cook.getPhoneNumber(),null,KitchenStaffStatus.INVITED,1,KitchenRole.CHEF));
        assertThrows(IllegalArgumentException.class,
                () -> projection.kitchen(event(staffId,other,cook.getPhoneNumber(),null,KitchenStaffStatus.INVITED,2,KitchenRole.CHEF)));
        assertEquals(org,kitchens.findById(staffId).orElseThrow().getOrganisationId());assertEquals(1,version(cook));
    }
}
