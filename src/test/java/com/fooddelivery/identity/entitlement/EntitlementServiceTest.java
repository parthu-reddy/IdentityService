package com.fooddelivery.identity.entitlement;

import com.fooddelivery.common.enums.*;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.entity.UserRole;
import com.fooddelivery.identity.entitlement.entity.*;
import com.fooddelivery.identity.entitlement.repository.*;
import com.fooddelivery.identity.entitlement.service.EntitlementService;
import com.fooddelivery.identity.organisation.entity.*;
import com.fooddelivery.identity.organisation.repository.*;
import com.fooddelivery.identity.port.CachePort;
import com.fooddelivery.identity.repository.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EntitlementServiceTest extends EntitlementH2Fixture {
    @Test void everyActivePersonIsCustomerAndStaffNeverElevatesEverydayToken() {
        var person=person();staff.saveAndFlush(UserRole.builder().user(person).roleName(RoleName.ADMIN).build());
        assertEquals(List.of(RoleName.CUSTOMER),entitlements.rolesFor(person.getId()));assertTrue(entitlements.isAdministrator(person.getId()));
    }
    @ParameterizedTest @EnumSource(ApplicationStatus.class)
    void onlyApprovedPersonalApplicationGrantsDelivery(ApplicationStatus status) {
        var person=person();rider(person,status);
        assertEquals(status==ApplicationStatus.APPROVED?List.of(RoleName.CUSTOMER,RoleName.DELIVERY):List.of(RoleName.CUSTOMER),entitlements.rolesFor(person.getId()));
    }
    @ParameterizedTest @EnumSource(ApplicationStatus.class)
    void onlyApprovedActiveBusinessGrantsRestaurantAndBusiness(ApplicationStatus status) {
        var person=person();business(organisation(person,OrganisationStatus.ACTIVE,MembershipStatus.ACTIVE),status);
        assertEquals(status==ApplicationStatus.APPROVED?List.of(RoleName.CUSTOMER,RoleName.RESTAURANT,RoleName.BUSINESS):List.of(RoleName.CUSTOMER),entitlements.rolesFor(person.getId()));
    }
    @ParameterizedTest @EnumSource(value=OrganisationStatus.class,names={"SUSPENDED","CLOSED"})
    void inactiveOrganisationCannotGrantPortal(OrganisationStatus status) {
        var person=person();business(organisation(person,status,MembershipStatus.ACTIVE),ApplicationStatus.APPROVED);
        assertEquals(List.of(RoleName.CUSTOMER),entitlements.rolesFor(person.getId()));
    }
    @Test void removedMembershipDoesNotGrantAccessButOtherActiveMembershipCan() {
        var person=person();business(organisation(person,OrganisationStatus.ACTIVE,MembershipStatus.REMOVED),ApplicationStatus.APPROVED);
        assertEquals(List.of(RoleName.CUSTOMER),entitlements.rolesFor(person.getId()));
        business(organisation(person,OrganisationStatus.ACTIVE,MembershipStatus.ACTIVE),ApplicationStatus.APPROVED);
        assertEquals(List.of(RoleName.CUSTOMER,RoleName.RESTAURANT,RoleName.BUSINESS),entitlements.rolesFor(person.getId()));
    }
    @Test void suspendedPersonHasNoTokenRolesButRemainsInspectableByAdmin() {
        var person=person();rider(person,ApplicationStatus.APPROVED);staff.saveAndFlush(UserRole.builder().user(person).roleName(RoleName.ADMIN).build());
        person.setActive(false);users.saveAndFlush(person);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->entitlements.rolesFor(person.getId()));
        assertEquals(List.of(RoleName.ADMIN),entitlements.allRolesFor(List.of(person)).get(person.getId()));
    }
    @Test void simultaneousRolesAreDeduplicatedAndFilteredPagesDoNotUseLegacyAssignments() {
        var person=person();rider(person,ApplicationStatus.APPROVED);business(organisation(person,OrganisationStatus.ACTIVE,MembershipStatus.ACTIVE),ApplicationStatus.APPROVED);
        business(organisation(person,OrganisationStatus.ACTIVE,MembershipStatus.ACTIVE),ApplicationStatus.APPROVED);
        var outsider=person();business(organisation(outsider,OrganisationStatus.ACTIVE,MembershipStatus.REMOVED),ApplicationStatus.APPROVED);
        assertEquals(List.of(RoleName.CUSTOMER,RoleName.DELIVERY,RoleName.RESTAURANT,RoleName.BUSINESS),entitlements.rolesFor(person.getId()));
        for(var role:List.of(RoleName.RESTAURANT,RoleName.DELIVERY,RoleName.BUSINESS)) {
            var page=users.findWithEntitlement(role.name(),PageRequest.of(0,1));assertEquals(1,page.getTotalElements());assertEquals(person.getId(),page.getContent().get(0).getId());
        }
        assertEquals(2,users.findWithEntitlement("CUSTOMER",PageRequest.of(0,1)).getTotalElements());
        assertEquals(0,users.findWithEntitlement("ADMIN",PageRequest.of(0,1)).getTotalElements());
    }
    @Test void administrativeRoleViewsUseConstantQueriesForOneAndFiftyPeople() {
        var people=new ArrayList<AppUser>();
        for(int i=0;i<50;i++){var person=person();people.add(person);rider(person,ApplicationStatus.APPROVED);business(organisation(person,OrganisationStatus.ACTIVE,MembershipStatus.ACTIVE),ApplicationStatus.APPROVED);}
        var stats=em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();em.clear();stats.clear();
        var one=entitlements.allRolesFor(people.subList(0,1));long oneQueries=stats.getPrepareStatementCount();
        em.clear();stats.clear();var fifty=entitlements.allRolesFor(people);long fiftyQueries=stats.getPrepareStatementCount();
        assertEquals(1,one.size());assertEquals(50,fifty.size());assertEquals(oneQueries,fiftyQueries);assertTrue(fiftyQueries<=5,"Five batched reads, no per-person queries");
        fifty.values().forEach(roles->assertEquals(List.of(RoleName.CUSTOMER,RoleName.DELIVERY,RoleName.RESTAURANT,RoleName.BUSINESS),roles));
    }
}
