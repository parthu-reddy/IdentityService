package com.fooddelivery.identity.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AuthControllerAuthorizationTest {

    @Test
    void onlyOtpInitiationAndVerificationAreAnonymous() throws Exception {
        assertNull(AuthController.class.getAnnotation(PreAuthorize.class));
        assertAuthorization("permitAll()", "initiateLogin", String.class, String.class);
        assertAuthorization("permitAll()", "verifyOtp", String.class, String.class, String.class,
                String.class, String.class, String.class, String.class);
        assertAuthorization("isAuthenticated()", "logout", String.class, String.class, String.class);
        assertAuthorization("isAuthenticated()", "getActiveSessions", String.class, String.class);
        assertAuthorization("isAuthenticated()", "removeSession", String.class, String.class, String.class);
        assertAuthorization("isAuthenticated()", "removeAllSessions", String.class, String.class);
    }

    private void assertAuthorization(String expected, String methodName, Class<?>... argumentTypes)
            throws NoSuchMethodException {
        Method method = AuthController.class.getMethod(methodName, argumentTypes);
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertEquals(expected, authorization.value(), methodName);
    }
}
