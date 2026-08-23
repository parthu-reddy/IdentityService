package com.fooddelivery.identity;

import com.fooddelivery.identity.controller.AuthController;
import com.fooddelivery.identity.service.AuthService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import com.fooddelivery.common.service.RateLimitingService;

public abstract class ContractTestBase {

    @BeforeEach
    public void setup() {
        AuthService authService = Mockito.mock(AuthService.class);
        RateLimitingService rateLimitingService = Mockito.mock(RateLimitingService.class);
        
        AuthController controller = new AuthController(authService, rateLimitingService);
        RestAssuredMockMvc.standaloneSetup(controller);
    }
}
