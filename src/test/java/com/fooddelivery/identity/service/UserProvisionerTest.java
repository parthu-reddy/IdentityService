package com.fooddelivery.identity.service;

import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The single account-creation site: both branches, and the shared status check. */
class UserProvisionerTest {
    final String phone = "8999123456";
    final UserRepository users = mock(UserRepository.class);
    final UserProvisioner provisioner = new UserProvisioner(users);

    @Test void findOrCreateReturnsTheExistingAccount() {
        var user = AppUser.builder().id(UUID.randomUUID()).phoneNumber(phone).isActive(true).build();
        when(users.findByPhoneNumber(phone)).thenReturn(Optional.of(user));
        when(users.lockById(user.getId())).thenReturn(Optional.of(user));
        var result = provisioner.findOrCreate(phone);
        assertSame(user, result.user());
        assertFalse(result.created());
        verify(users, never()).saveAndFlush(any());
    }

    @Test void findOrCreateCreatesAMissingAccount() {
        var saved = AppUser.builder().id(UUID.randomUUID()).phoneNumber(phone).isActive(true).build();
        when(users.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(users.saveAndFlush(any())).thenReturn(saved);
        when(users.lockById(saved.getId())).thenReturn(Optional.of(saved));
        var result = provisioner.findOrCreate(phone);
        assertTrue(result.created());
        assertSame(saved, result.user());
        var created = org.mockito.ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(created.capture());
        assertEquals(phone, created.getValue().getPhoneNumber());
        assertTrue(created.getValue().isActive());
        assertNull(created.getValue().getName(), "a pre-created account stays nameless so the first sign-in prompts for a profile");
    }

    @Test void deactivatedAccountIsNeverRevived() {
        var user = AppUser.builder().id(UUID.randomUUID()).phoneNumber(phone).isActive(false).build();
        when(users.findByPhoneNumber(phone)).thenReturn(Optional.of(user));
        when(users.lockById(user.getId())).thenReturn(Optional.of(user));
        var ex = assertThrows(AccountUnavailableException.class, () -> provisioner.findOrCreate(phone));
        assertEquals(401, ex.getStatusCode().value());
        assertFalse(user.isActive());
        verify(users, never()).saveAndFlush(any());
    }
}
