package com.fooddelivery.identity.service;

import com.fooddelivery.common.enums.RoleName;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.entitlement.service.*;
import com.fooddelivery.identity.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InternalUserServicePhoneLookupTest {
    final UserRepository users=mock(UserRepository.class);
    final EntitlementService entitlements=mock(EntitlementService.class);
    final InternalUserService service=new InternalUserService(users,mock(UserRoleRepository.class),mock(AuthService.class),entitlements,mock(EntitlementVersionPublisher.class));
    @Test void adminPhoneLookupReturnsComputedRoles() {
        var person=AppUser.builder().id(UUID.randomUUID()).phoneNumber("8000000001").isActive(true).build();
        when(users.findByPhoneNumber(person.getPhoneNumber())).thenReturn(Optional.of(person));
        when(entitlements.allRolesFor(List.of(person))).thenReturn(Map.of(person.getId(),List.of(RoleName.CUSTOMER,RoleName.DELIVERY)));
        var result=service.getUserByPhone(person.getPhoneNumber());assertEquals(person.getId(),result.getId());assertEquals(person.getPhoneNumber(),result.getPhoneNumber());
        assertEquals(List.of(RoleName.CUSTOMER,RoleName.DELIVERY),result.getRoles());
    }
    @Test void unknownPhoneIsNotFound() {
        when(users.findByPhoneNumber("8000000999")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(ResponseStatusException.class,()->service.getUserByPhone("8000000999")).getStatusCode());
    }
    @Test void malformedPhoneNeverReachesRepository() {
        for(var malformed:new String[]{"+918000000001","80000 00001","800000000","abc",""})
            assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,()->service.getUserByPhone(malformed)).getStatusCode());
        verifyNoInteractions(users,entitlements);
    }
}
