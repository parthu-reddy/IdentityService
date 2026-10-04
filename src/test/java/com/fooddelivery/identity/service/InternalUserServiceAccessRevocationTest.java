package com.fooddelivery.identity.service;

import com.fooddelivery.common.enums.RoleName;
import com.fooddelivery.identity.entity.*;
import com.fooddelivery.identity.entitlement.service.*;
import com.fooddelivery.identity.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InternalUserServiceAccessRevocationTest {
    final UserRepository users=mock(UserRepository.class);
    final UserRoleRepository staff=mock(UserRoleRepository.class);
    final AuthService auth=mock(AuthService.class);
    final EntitlementService entitlements=mock(EntitlementService.class);
    final EntitlementVersionPublisher versions=mock(EntitlementVersionPublisher.class);
    final InternalUserService service=new InternalUserService(users,staff,auth,entitlements,versions);
    final AppUser person=AppUser.builder().id(UUID.randomUUID()).isActive(true).phoneNumber("9000000001").build();
    final AppUser second=AppUser.builder().id(UUID.randomUUID()).isActive(true).phoneNumber("9000000002").build();
    UserRole role(AppUser user) {return UserRole.builder().user(user).roleName(RoleName.ADMIN).build();}
    void fixture(List<UserRole> admins) {when(users.lockById(person.getId())).thenReturn(Optional.of(person));when(staff.findByRoleNameForUpdate(RoleName.ADMIN)).thenReturn(admins);}
    @Test void suspensionBumpsVersionAndRevokesAllSessions() {
        fixture(List.of());service.updateUserStatus(person.getId(),false);
        assertFalse(person.isActive());verify(users).saveAndFlush(person);verify(versions).bump(List.of(person.getId()));verify(auth).removeAllSessions(person.getId());
    }
    @Test void staffGrantBumpsVersionAndRevokesOnlyAfterCommit() {
        fixture(List.of());when(staff.findByUserId(person.getId())).thenReturn(List.of());
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.addRoleToUser(person.getId(),RoleName.ADMIN);verifyNoInteractions(auth);
            var assignment=org.mockito.ArgumentCaptor.forClass(UserRole.class);verify(staff).saveAndFlush(assignment.capture());
            assertEquals(RoleName.ADMIN,assignment.getValue().getRoleName());verify(versions).bump(List.of(person.getId()));
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(auth).removeAllSessions(person.getId());
        } finally {TransactionSynchronizationManager.clearSynchronization();}
    }
    @Test void computedPortalRolesCannotBeManuallyGrantedOrRemoved() {
        for(var role:RoleName.values()) if(role!=RoleName.ADMIN) {
            assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,()->service.addRoleToUser(person.getId(),role)).getStatusCode());
            assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,()->service.removeRoleFromUser(person.getId(),role)).getStatusCode());
        }
        verifyNoInteractions(users,staff,versions,auth);
    }
    @Test void removalBumpsAndRevokesSessions() {
        fixture(List.of(role(person),role(second)));service.removeRoleFromUser(person.getId(),RoleName.ADMIN);
        verify(staff).deleteByUserIdAndRoleName(person.getId(),RoleName.ADMIN);verify(versions).bump(List.of(person.getId()));verify(auth).removeAllSessions(person.getId());
    }
    @Test void finalActiveAdministratorCannotBeRemovedOrSuspended() {
        second.setActive(false);fixture(List.of(role(person),role(second)));
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,()->service.removeRoleFromUser(person.getId(),RoleName.ADMIN)).getStatusCode());
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,()->service.updateUserStatus(person.getId(),false)).getStatusCode());
        verify(staff,never()).deleteByUserIdAndRoleName(any(),any());verifyNoInteractions(versions,auth);assertTrue(person.isActive());
    }
    @Test void selfRemovalAndSuspensionAreRefused() {
        fixture(List.of(role(person),role(second)));
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,()->service.removeRoleFromUser(person.getId(),RoleName.ADMIN,person.getId())).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,()->service.updateUserStatus(person.getId(),false,person.getId())).getStatusCode());
        verifyNoInteractions(versions,auth);
    }
    @Test void unchangedStatusAndExistingStaffAssignmentDoNotRevoke() {
        fixture(List.of(role(person)));when(staff.findByUserId(person.getId())).thenReturn(List.of(role(person)));
        service.updateUserStatus(person.getId(),true);service.addRoleToUser(person.getId(),RoleName.ADMIN);verifyNoInteractions(versions,auth);
    }
    @Test void noRevocationOccursForRolledBackStaffWrite() {
        fixture(List.of());when(staff.findByUserId(person.getId())).thenReturn(List.of());
        TransactionSynchronizationManager.initSynchronization();
        try {service.addRoleToUser(person.getId(),RoleName.ADMIN);
            TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));verifyNoInteractions(auth);
        } finally {TransactionSynchronizationManager.clearSynchronization();}
    }
}
