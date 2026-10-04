package com.fooddelivery.identity.controller;

import com.fooddelivery.identity.dto.UpdateProfileRequest;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserProfilePhoneSecurityTest {
    @Test void unverifiedPhoneChangeIsRejectedBeforeAnyProfileMutation() {
        var repository=mock(UserRepository.class);UUID id=UUID.randomUUID();
        var person=AppUser.builder().id(id).phoneNumber("8999000001").build();
        when(repository.findById(id)).thenReturn(Optional.of(person));
        var response=new UserController(repository).updateProfile(id.toString(),new UpdateProfileRequest("New name","fixture@example.invalid","8999000002"));
        assertEquals(400,response.getStatusCode().value());
        assertEquals("8999000001",person.getPhoneNumber());assertNull(person.getName());assertNull(person.getEmail());
        verify(repository,never()).save(any());
    }
    @Test void verifiedPhoneStillAllowsFirstProfileCompletion() {
        var repository=mock(UserRepository.class);UUID id=UUID.randomUUID();
        var person=AppUser.builder().id(id).phoneNumber("8999000001").build();when(repository.findById(id)).thenReturn(Optional.of(person));
        assertEquals(200,new UserController(repository).updateProfile(id.toString(),new UpdateProfileRequest("Fixture","fixture@example.invalid","8999000001")).getStatusCode().value());
        assertEquals("Fixture",person.getName());verify(repository).save(person);
    }
}
