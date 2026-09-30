package com.fooddelivery.identity.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.identity.port.CachePort;
import com.fooddelivery.identity.service.E2eRunnerSecretVerifier;
import com.fooddelivery.identity.service.E2eSeededAccountAllowlist;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class E2eOtpControllerTest {

    private CachePort cachePort;
    private E2eOtpController controller;

    @BeforeEach
    void setUp() {
        cachePort = mock(CachePort.class);
        controller = new E2eOtpController(cachePort,
                new E2eRunnerSecretVerifier("runner-secret"), new E2eSeededAccountAllowlist());
    }

    @Test
    void directOtpLookupRejectsAnAbsentRunnerSecret() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.getOtp(null, "8000000001", "CUSTOMER"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void directOtpLookupRejectsAnIncorrectRunnerSecret() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.getOtp(List.of("wrong-secret"), "8000000001", "CUSTOMER"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void directOtpLookupRejectsDuplicatedRunnerSecretHeaders() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.getOtp(List.of("runner-secret", "runner-secret"),
                        "8000000001", "CUSTOMER"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void directOtpLookupRejectsASecretForAnUnseededOrWrongPortalAccount() {
        ResponseStatusException unseeded = assertThrows(ResponseStatusException.class,
                () -> controller.getOtp(List.of("runner-secret"), "8000000501", "CUSTOMER"));
        ResponseStatusException wrongPortal = assertThrows(ResponseStatusException.class,
                () -> controller.getOtp(List.of("runner-secret"), "8000000001", "ADMIN"));

        assertEquals(HttpStatus.FORBIDDEN, unseeded.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, wrongPortal.getStatusCode());
    }

    @Test
    void directOtpLookupReturnsOnlyTheRequestedSeededPortalOtp() {
        when(cachePort.get("OTP:8000000001:customer")).thenReturn("123456");

        ApiResponse<String> response = controller.getOtp(List.of("runner-secret"), "8000000001", "CUSTOMER")
                .getBody();

        assertEquals("123456", response.getData());
    }
}
