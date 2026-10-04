package com.fooddelivery.identity.entitlement;

import com.fooddelivery.common.enums.*;
import com.fooddelivery.identity.entitlement.dto.PortalViews.*;
import com.fooddelivery.identity.entitlement.service.PortalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the same real embedded H2 projections as the D5 matrix. */
class PortalServiceTest extends EntitlementH2Fixture {
    @Autowired PortalService portals;
    PortalView view(java.util.UUID id,Portal portal){return portals.portals(id).stream().filter(v->v.portal()==portal).findFirst().orElseThrow();}
    @Test void newCustomerSeesApplicationStartsAndStaffOnlySeesStepUp() {
        var person=person();assertEquals(State.AVAILABLE,view(person.getId(),Portal.CUSTOMER).state());
        for(var portal:java.util.List.of(Portal.RESTAURANT,Portal.DELIVERY,Portal.BUSINESS,Portal.ADS))assertEquals(State.NOT_STARTED,view(person.getId(),portal).state());
        assertEquals(State.UNAVAILABLE,view(person.getId(),Portal.ADMIN).state());
        staff.saveAndFlush(com.fooddelivery.identity.entity.UserRole.builder().user(person).roleName(RoleName.ADMIN).build());
        assertEquals(State.STEP_UP_REQUIRED,view(person.getId(),Portal.ADMIN).state());
    }
    @ParameterizedTest @EnumSource(ApplicationStatus.class)
    void applicationStateAndReasonComeFromProjection(ApplicationStatus status) {
        var person=person();var org=organisation(person,OrganisationStatus.ACTIVE,MembershipStatus.ACTIVE);business(org,status);rider(person,status);
        State expected=switch(status){case APPROVED->State.AVAILABLE;case IN_REVIEW,SUBMITTED->State.PENDING_REVIEW;case DRAFT,REJECTED->State.ACTION_REQUIRED;case SUSPENDED->State.SUSPENDED;};
        for(var portal:java.util.List.of(Portal.RESTAURANT,Portal.DELIVERY,Portal.BUSINESS,Portal.ADS))assertEquals(expected,view(person.getId(),portal).state());
        var business=businesses.findAllByOrganisationIdIn(java.util.List.of(org.getId())).get(0);business.setReason("Update required documents");businesses.saveAndFlush(business);
        if(status!=ApplicationStatus.APPROVED)assertEquals("Update required documents",view(person.getId(),Portal.RESTAURANT).reason());
    }
    @Test void emptyOrganisationNeedsActionAndSuspensionOverridesApproval() {
        var person=person();var org=organisation(person,OrganisationStatus.ACTIVE,MembershipStatus.ACTIVE);
        assertEquals(State.ACTION_REQUIRED,view(person.getId(),Portal.BUSINESS).state());business(org,ApplicationStatus.APPROVED);
        org.setStatus(OrganisationStatus.SUSPENDED);organisations.saveAndFlush(org);
        assertEquals(State.SUSPENDED,view(person.getId(),Portal.RESTAURANT).state());assertEquals(State.SUSPENDED,view(person.getId(),Portal.BUSINESS).state());
        assertEquals(OrganisationRole.OWNER,view(person.getId(),Portal.BUSINESS).organisations().get(0).role());
    }
}
