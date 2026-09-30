package com.fooddelivery.identity.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminUserControllerAuthorizationTest {

    @Test
    void userDetailLookupRequiresAdministratorRole() throws Exception {
        PreAuthorize authorization = AdminUserController.class
                .getMethod("getUser", UUID.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("hasRole('ADMIN')", authorization.value());
    }
}
