package com.fooddelivery.identity;

import com.fooddelivery.identity.controller.AuthController;
import com.fooddelivery.identity.service.AuthService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

public abstract class ContractTestBase {

    @BeforeEach
    public void setup() {
        AuthService authService = Mockito.mock(AuthService.class);
        
        AuthController controller = new AuthController(authService);
        RestAssuredMockMvc.standaloneSetup(controller);
    }
}
