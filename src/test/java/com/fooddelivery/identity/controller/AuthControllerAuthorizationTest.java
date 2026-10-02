package com.fooddelivery.identity.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AuthControllerAuthorizationTest {

    @Test
    void onlyOtpEntryPointsAreAnonymous() throws Exception {
        assertNull(AuthController.class.getAnnotation(PreAuthorize.class));
        assertAuthorization("permitAll()", "initiateLogin", String.class, String.class);
        assertAuthorization("permitAll()", "verifyOtp", String.class, String.class, String.class,
                String.class, String.class, String.class, String.class);
        assertAuthorization("permitAll()", "registerWithOtp", String.class, String.class, String.class,
                String.class, String.class, String.class, String.class);
        assertAuthorization("isAuthenticated()", "logout", String.class, String.class, String.class);
        assertAuthorization("isAuthenticated()", "getActiveSessions", String.class, String.class);
        assertAuthorization("isAuthenticated()", "removeSession", String.class, String.class, String.class);
        assertAuthorization("isAuthenticated()", "removeAllSessions", String.class, String.class);
    }

    @Test
    @org.junit.jupiter.api.Tag("auth-rate-limit")
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "auth.limits.enabled", matches = "true")
    void emptyControllerBucketReturns429WithoutInitiatingOrVerifying() {
        var service = org.mockito.Mockito.mock(com.fooddelivery.identity.service.AuthService.class);
        var limits = org.mockito.Mockito.mock(com.fooddelivery.common.service.RateLimitingService.class);
        var bucket = org.mockito.Mockito.mock(io.github.bucket4j.Bucket.class);
        org.mockito.Mockito.when(limits.resolveBucket(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(java.time.Duration.class))).thenReturn(bucket);
        org.mockito.Mockito.when(bucket.tryConsume(1)).thenReturn(false);
        var controller = new AuthController(service, limits);
        assertEquals(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                controller.initiateLogin("8000000001", "CUSTOMER").getStatusCode());
        assertEquals(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                controller.verifyOtp("8000000001", "123456", "CUSTOMER", "test", "test", "test", null).getStatusCode());
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    private void assertAuthorization(String expected, String methodName, Class<?>... argumentTypes)
            throws NoSuchMethodException {
        Method method = AuthController.class.getMethod(methodName, argumentTypes);
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertEquals(expected, authorization.value(), methodName);
    }
}
