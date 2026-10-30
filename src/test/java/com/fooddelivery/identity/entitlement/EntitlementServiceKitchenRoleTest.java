package com.fooddelivery.identity.entitlement;

import com.fooddelivery.common.enums.*;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.entitlement.entity.KitchenEntitlement;
import com.fooddelivery.identity.entitlement.repository.KitchenEntitlementRepository;
import com.fooddelivery.identity.entitlement.service.EntitlementVersionPublisher;
import com.fooddelivery.identity.organisation.entity.Organisation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** KITCHEN comes from a live kitchen staff row in an ACTIVE organisation with an APPROVED restaurant (CROSSCHECK X-6). */
class EntitlementServiceKitchenRoleTest extends EntitlementH2Fixture {
    @Autowired KitchenEntitlementRepository kitchens;
    @Autowired EntitlementVersionPublisher versions;

    /** An organisation the person is not a member of: kitchen staff never gain RESTAURANT/BUSINESS. */
    Organisation restaurant(OrganisationStatus status,ApplicationStatus application) {
        var org=organisation(person(),status,MembershipStatus.ACTIVE);business(org,application);return org;
    }
    KitchenEntitlement staff(Organisation org,String phone,UUID userId,KitchenStaffStatus status) {
        return kitchens.saveAndFlush(KitchenEntitlement.builder().staffId(UUID.randomUUID()).organisationId(org.getId()).outletId(UUID.randomUUID())
                .phoneNumber(phone).userId(userId).manager(false).chef(true).status(status).staffVersion(1).updatedAt(now).build());
    }
    static final List<RoleName> KITCHEN=List.of(RoleName.CUSTOMER,RoleName.KITCHEN), CUSTOMER=List.of(RoleName.CUSTOMER);

    @Test void anUnboundInvitationGrantsKitchenToWhoeverSignsInWithThePhone() {
        var person=person();staff(restaurant(OrganisationStatus.ACTIVE,ApplicationStatus.APPROVED),person.getPhoneNumber(),null,KitchenStaffStatus.INVITED);
        assertEquals(KITCHEN,entitlements.rolesFor(person.getId()));
    }
    @Test void aBoundRowGrantsItsUserAndNoLongerTheCurrentHolderOfThePhone() {
        var bound=person();var phoneHolder=person();
        staff(restaurant(OrganisationStatus.ACTIVE,ApplicationStatus.APPROVED),phoneHolder.getPhoneNumber(),bound.getId(),KitchenStaffStatus.ACTIVE);
        assertEquals(KITCHEN,entitlements.rolesFor(bound.getId()));
        assertEquals(CUSTOMER,entitlements.rolesFor(phoneHolder.getId()));
    }
    @Test void aWaiterOnlyRowGrantsKitchen() {
        var person=person();
        kitchens.saveAndFlush(KitchenEntitlement.builder().staffId(UUID.randomUUID()).organisationId(restaurant(OrganisationStatus.ACTIVE,ApplicationStatus.APPROVED).getId())
                .outletId(UUID.randomUUID()).phoneNumber(person.getPhoneNumber()).userId(person.getId()).manager(false).chef(false).waiter(true)
                .status(KitchenStaffStatus.ACTIVE).staffVersion(1).updatedAt(now).build());
        assertEquals(KITCHEN,entitlements.rolesFor(person.getId()));
    }
    @Test void removedStaffHaveNoKitchenRole() {
        var person=person();var org=restaurant(OrganisationStatus.ACTIVE,ApplicationStatus.APPROVED);
        staff(org,person.getPhoneNumber(),null,KitchenStaffStatus.REMOVED);staff(org,person.getPhoneNumber(),person.getId(),KitchenStaffStatus.REMOVED);
        assertEquals(CUSTOMER,entitlements.rolesFor(person.getId()));
        assertEquals(CUSTOMER,entitlements.allRolesFor(List.of(person)).get(person.getId()));
    }
    @ParameterizedTest @EnumSource(value=OrganisationStatus.class,names={"SUSPENDED","CLOSED"})
    void anInactiveOrganisationGrantsNoKitchenRole(OrganisationStatus status) {
        var person=person();staff(restaurant(status,ApplicationStatus.APPROVED),person.getPhoneNumber(),person.getId(),KitchenStaffStatus.ACTIVE);
        assertEquals(CUSTOMER,entitlements.rolesFor(person.getId()));
        assertEquals(CUSTOMER,entitlements.allRolesFor(List.of(person)).get(person.getId()));
    }
    @ParameterizedTest @EnumSource(value=ApplicationStatus.class,names="APPROVED",mode=EnumSource.Mode.EXCLUDE)
    void anUnapprovedRestaurantGrantsNoKitchenRole(ApplicationStatus status) {
        var person=person();staff(restaurant(OrganisationStatus.ACTIVE,status),person.getPhoneNumber(),null,KitchenStaffStatus.INVITED);
        assertEquals(CUSTOMER,entitlements.rolesFor(person.getId()));
        assertEquals(CUSTOMER,entitlements.allRolesFor(List.of(person)).get(person.getId()));
    }
    @Test void kitchenStaffNeverGainOrganisationRolesAndOneLiveRowAmongDeadOnesIsEnough() {
        var person=person();
        staff(restaurant(OrganisationStatus.SUSPENDED,ApplicationStatus.APPROVED),person.getPhoneNumber(),person.getId(),KitchenStaffStatus.ACTIVE);
        staff(restaurant(OrganisationStatus.ACTIVE,ApplicationStatus.APPROVED),person.getPhoneNumber(),null,KitchenStaffStatus.REMOVED);
        assertEquals(CUSTOMER,entitlements.rolesFor(person.getId()));
        staff(restaurant(OrganisationStatus.ACTIVE,ApplicationStatus.APPROVED),person.getPhoneNumber(),null,KitchenStaffStatus.INVITED);
        assertEquals(KITCHEN,entitlements.rolesFor(person.getId()));
    }
    @Test void bulkRolesAgreeWithSingleRolesForAMixedBatch() {
        var open=restaurant(OrganisationStatus.ACTIVE,ApplicationStatus.APPROVED);var closed=restaurant(OrganisationStatus.CLOSED,ApplicationStatus.APPROVED);
        var byPhone=person();staff(open,byPhone.getPhoneNumber(),null,KitchenStaffStatus.INVITED);
        var byUser=person();staff(open,byUser.getPhoneNumber(),byUser.getId(),KitchenStaffStatus.ACTIVE);rider(byUser,ApplicationStatus.APPROVED);
        var removed=person();staff(open,removed.getPhoneNumber(),removed.getId(),KitchenStaffStatus.REMOVED);
        var closedOrg=person();staff(closed,closedOrg.getPhoneNumber(),closedOrg.getId(),KitchenStaffStatus.ACTIVE);
        var owner=person();business(organisation(owner,OrganisationStatus.ACTIVE,MembershipStatus.ACTIVE),ApplicationStatus.APPROVED);
        var people=List.of(byPhone,byUser,removed,closedOrg,owner);
        var bulk=entitlements.allRolesFor(people);
        for(var p:people)assertEquals(entitlements.rolesFor(p.getId()),bulk.get(p.getId()),"bulk == single");
        assertEquals(List.of(RoleName.CUSTOMER,RoleName.DELIVERY,RoleName.KITCHEN),bulk.get(byUser.getId()));
        assertEquals(KITCHEN,bulk.get(byPhone.getId()));
        var suspended=person();staff(open,suspended.getPhoneNumber(),null,KitchenStaffStatus.INVITED);suspended.setActive(false);users.saveAndFlush(suspended);
        assertEquals(List.of(),entitlements.allRolesFor(List.of(suspended)).get(suspended.getId()),"a suspended person holds no token role");
    }
    @Test void bulkKitchenRolesUseConstantQueriesForOneAndFiftyPeople() {
        var open=restaurant(OrganisationStatus.ACTIVE,ApplicationStatus.APPROVED);var people=new ArrayList<AppUser>();
        for(int i=0;i<50;i++){var p=person();people.add(p);staff(open,p.getPhoneNumber(),i%2==0?null:p.getId(),KitchenStaffStatus.ACTIVE);}
        var stats=em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();em.clear();stats.clear();
        entitlements.allRolesFor(people.subList(0,1));long one=stats.getPrepareStatementCount();
        em.clear();stats.clear();var fifty=entitlements.allRolesFor(people);long many=stats.getPrepareStatementCount();
        assertEquals(one,many);fifty.values().forEach(r->assertEquals(KITCHEN,r));
    }
    @Test void adminRoleFilterListsLiveKitchenStaffOnly() {
        var open=restaurant(OrganisationStatus.ACTIVE,ApplicationStatus.APPROVED);
        var byPhone=person();staff(open,byPhone.getPhoneNumber(),null,KitchenStaffStatus.INVITED);
        var byUser=person();staff(open,byUser.getPhoneNumber(),byUser.getId(),KitchenStaffStatus.ACTIVE);
        var removed=person();staff(open,removed.getPhoneNumber(),removed.getId(),KitchenStaffStatus.REMOVED);
        var suspendedOrg=person();staff(restaurant(OrganisationStatus.SUSPENDED,ApplicationStatus.APPROVED),suspendedOrg.getPhoneNumber(),null,KitchenStaffStatus.INVITED);
        var found=users.findWithEntitlement("KITCHEN",PageRequest.of(0,10)).getContent().stream().map(AppUser::getId).toList();
        assertEquals(Set.of(byPhone.getId(),byUser.getId()),Set.copyOf(found));
    }
    @Test void organisationTransitionsBumpBoundKitchenUsersAndTheHoldersOfUnboundPhones() {
        var org=restaurant(OrganisationStatus.ACTIVE,ApplicationStatus.APPROVED);
        var bound=person();var byPhone=person();var removed=person();var outsider=person();
        staff(org,bound.getPhoneNumber(),bound.getId(),KitchenStaffStatus.ACTIVE);staff(org,byPhone.getPhoneNumber(),null,KitchenStaffStatus.INVITED);
        staff(org,removed.getPhoneNumber(),removed.getId(),KitchenStaffStatus.REMOVED);
        versions.bumpOrganisation(org.getId());em.clear();
        for(var p:List.of(bound,byPhone))assertEquals(1,users.findById(p.getId()).orElseThrow().getEntitlementsVersion());
        for(var p:List.of(removed,outsider))assertEquals(0,users.findById(p.getId()).orElseThrow().getEntitlementsVersion());
    }
}
