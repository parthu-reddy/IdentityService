package com.fooddelivery.identity.entitlement;

import com.fooddelivery.common.enums.*;
import com.fooddelivery.identity.entitlement.dto.PortalViews.*;
import com.fooddelivery.identity.entitlement.entity.KitchenEntitlement;
import com.fooddelivery.identity.entitlement.repository.KitchenEntitlementRepository;
import com.fooddelivery.identity.entitlement.service.PortalService;
import com.fooddelivery.identity.organisation.entity.Organisation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The Kitchen launcher tile: AVAILABLE / SUSPENDED / UNAVAILABLE (hidden, like ADMIN for non-staff). */
class PortalServiceKitchenTest extends EntitlementH2Fixture {
    @Autowired PortalService portals;
    @Autowired KitchenEntitlementRepository kitchens;

    PortalView kitchen(UUID id) {
        var views=portals.portals(id).stream().filter(v->v.portal()==Portal.KITCHEN).toList();
        assertEquals(1,views.size());return views.get(0);
    }
    Organisation restaurant(OrganisationStatus status,ApplicationStatus application) {
        var org=organisation(person(),status,MembershipStatus.ACTIVE);business(org,application);return org;
    }
    void staff(Organisation org,String phone,UUID userId,KitchenStaffStatus status) {
        kitchens.saveAndFlush(KitchenEntitlement.builder().staffId(UUID.randomUUID()).organisationId(org.getId()).outletId(UUID.randomUUID())
                .phoneNumber(phone).userId(userId).manager(true).chef(false).status(status).staffVersion(1).updatedAt(now).build());
    }

    @Test void aLiveRowInAnOpenRestaurantIsAvailableWithoutOrganisationDetails() {
        var byPhone=person();var byUser=person();var org=restaurant(OrganisationStatus.ACTIVE,ApplicationStatus.APPROVED);
        staff(org,byPhone.getPhoneNumber(),null,KitchenStaffStatus.INVITED);staff(org,byUser.getPhoneNumber(),byUser.getId(),KitchenStaffStatus.ACTIVE);
        for(var p:List.of(byPhone,byUser)) {
            var view=kitchen(p.getId());
            assertEquals(State.AVAILABLE,view.state());assertEquals(List.of(),view.organisations());assertNull(view.reason());
            assertEquals(State.NOT_STARTED,portals.portals(p.getId()).stream().filter(v->v.portal()==Portal.RESTAURANT).findFirst().orElseThrow().state(),
                    "kitchen staff are not organisation members");
        }
    }
    @ParameterizedTest @EnumSource(value=OrganisationStatus.class,names={"SUSPENDED","CLOSED"})
    void liveRowsOnlyInClosedOrganisationsAreSuspended(OrganisationStatus status) {
        var person=person();staff(restaurant(status,ApplicationStatus.APPROVED),person.getPhoneNumber(),person.getId(),KitchenStaffStatus.ACTIVE);
        var view=kitchen(person.getId());
        assertEquals(State.SUSPENDED,view.state());assertEquals("Restaurant access is suspended or closed",view.reason());
        staff(restaurant(OrganisationStatus.ACTIVE,ApplicationStatus.APPROVED),person.getPhoneNumber(),null,KitchenStaffStatus.INVITED);
        assertEquals(State.AVAILABLE,kitchen(person.getId()).state(),"one open kitchen is enough");
    }
    @Test void anUnapprovedRestaurantIsSuspended() {
        var person=person();staff(restaurant(OrganisationStatus.ACTIVE,ApplicationStatus.SUSPENDED),person.getPhoneNumber(),null,KitchenStaffStatus.INVITED);
        assertEquals(State.SUSPENDED,kitchen(person.getId()).state());
    }
    @Test void noRowOrOnlyRemovedRowsIsUnavailable() {
        var person=person();assertEquals(State.UNAVAILABLE,kitchen(person.getId()).state());
        var org=restaurant(OrganisationStatus.ACTIVE,ApplicationStatus.APPROVED);
        staff(org,person.getPhoneNumber(),person.getId(),KitchenStaffStatus.REMOVED);
        var other=person();staff(org,person.getPhoneNumber(),other.getId(),KitchenStaffStatus.ACTIVE);
        assertEquals(State.UNAVAILABLE,kitchen(person.getId()).state(),"a row bound to someone else no longer follows the phone");
    }
}
