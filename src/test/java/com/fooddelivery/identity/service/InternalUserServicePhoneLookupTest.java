package com.fooddelivery.identity.service;

import com.fooddelivery.common.enums.RoleName;
import com.fooddelivery.identity.dto.UserDTO;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.entity.UserRole;
import com.fooddelivery.identity.repository.UserRepository;
import com.fooddelivery.identity.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The admin user search says "User ID / Phone", and a phone number went to GET /{id}, which
 * only takes a UUID -- support could not find a customer by the number they sign in with.
 */
class InternalUserServicePhoneLookupTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final InternalUserService service = new InternalUserService(users, roles);

    @Test
    void findsTheUserByTheNumberTheySignInWith_withEveryRole() {
        UUID id = UUID.randomUUID();
        AppUser user = AppUser.builder().id(id).phoneNumber("8000000001").isActive(true).build();
        when(users.findByPhoneNumber("8000000001")).thenReturn(Optional.of(user));
        when(roles.findByUserId(id)).thenReturn(List.of(
                UserRole.builder().roleName(RoleName.CUSTOMER).serviceName("CustomerApplication").build(),
                UserRole.builder().roleName(RoleName.DELIVERY).serviceName("DeliveryExecutiveApplication").build()));

        UserDTO found = service.getUserByPhone("8000000001");

        assertThat(found.getId()).isEqualTo(id);
        assertThat(found.getPhoneNumber()).isEqualTo("8000000001");
        assertThat(found.getRoles()).containsExactly(RoleName.CUSTOMER, RoleName.DELIVERY);
    }

    @Test
    void anUnknownNumberIsNotFound_notUnauthorized() {
        when(users.findByPhoneNumber("8000000999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserByPhone("8000000999"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void refusesAnythingButTheTenDigitsTheLoginStores() {
        for (String malformed : new String[] {"+918000000001", "80000 00001", "800000000", "abc", ""}) {
            assertThatThrownBy(() -> service.getUserByPhone(malformed))
                    .as(malformed)
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(users, never()).findByPhoneNumber(org.mockito.ArgumentMatchers.anyString());
    }
}
