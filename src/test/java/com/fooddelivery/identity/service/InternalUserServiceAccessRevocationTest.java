package com.fooddelivery.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fooddelivery.common.enums.RoleName;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.entity.UserRole;
import com.fooddelivery.identity.repository.UserRepository;
import com.fooddelivery.identity.repository.UserRoleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class InternalUserServiceAccessRevocationTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final InternalUserService service = new InternalUserService(users, roles, authService);

    @Test
    void suspensionRevokesAllExistingSessions() {
        AppUser user = activeUser();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        service.updateUserStatus(user.getId(), false);

        assertEquals(false, user.isActive());
        verify(users).save(user);
        verify(authService).removeAllSessions(user.getId());
    }

    @Test
    void addingRoleUsesServerOwnedPortalAndRevokesSessions() {
        AppUser user = activeUser();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(roles.findByUserId(user.getId())).thenReturn(List.of());

        service.addRoleToUser(user.getId(), RoleName.DELIVERY);

        org.mockito.ArgumentCaptor<UserRole> assignment = org.mockito.ArgumentCaptor.forClass(UserRole.class);
        verify(roles).save(assignment.capture());
        assertEquals(RoleName.DELIVERY, assignment.getValue().getRoleName());
        assertEquals("DeliveryExecutiveApplication", assignment.getValue().getServiceName());
        verify(authService).removeAllSessions(user.getId());
    }

    @Test
    void removingRoleDeletesLegacyAliasesAndRevokesSessions() {
        AppUser user = activeUser();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(roles.findByUserId(user.getId())).thenReturn(List.of(
                UserRole.builder().user(user).roleName(RoleName.ADMIN).serviceName("ADMIN").build(),
                UserRole.builder().user(user).roleName(RoleName.ADMIN).serviceName("AdminApplication").build()));
        when(roles.findByRoleNameForUpdate(RoleName.ADMIN)).thenReturn(List.of(
                UserRole.builder().user(user).roleName(RoleName.ADMIN).serviceName("ADMIN").build(),
                UserRole.builder().user(user).roleName(RoleName.ADMIN).serviceName("AdminApplication").build(),
                UserRole.builder().user(activeUser()).roleName(RoleName.ADMIN).serviceName("ADMIN").build()));

        service.removeRoleFromUser(user.getId(), RoleName.ADMIN);

        verify(roles).deleteByUserIdAndRoleName(user.getId(), RoleName.ADMIN);
        verify(authService).removeAllSessions(user.getId());
    }

    @Test
    void lastAdministratorCannotBeRemoved() {
        AppUser admin = activeUser();
        UserRole assignment = UserRole.builder().user(admin).roleName(RoleName.ADMIN).serviceName("ADMIN").build();
        when(users.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(roles.findByUserId(admin.getId())).thenReturn(List.of(assignment));
        when(roles.findByRoleNameForUpdate(RoleName.ADMIN)).thenReturn(List.of(assignment));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.removeRoleFromUser(admin.getId(), RoleName.ADMIN));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(roles, never()).deleteByUserIdAndRoleName(admin.getId(), RoleName.ADMIN);
        verify(authService, never()).removeAllSessions(admin.getId());
    }

    @Test
    void administratorCannotRemoveOwnAdminRole() {
        AppUser admin = activeUser();
        when(users.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(roles.findByUserId(admin.getId())).thenReturn(List.of(
                UserRole.builder().user(admin).roleName(RoleName.ADMIN).serviceName("ADMIN").build()));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.removeRoleFromUser(admin.getId(), RoleName.ADMIN, admin.getId()));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(roles, never()).deleteByUserIdAndRoleName(admin.getId(), RoleName.ADMIN);
        verify(authService, never()).removeAllSessions(admin.getId());
    }

    private static AppUser activeUser() {
        return AppUser.builder().id(UUID.randomUUID()).phoneNumber("9000000001").isActive(true).build();
    }
}
