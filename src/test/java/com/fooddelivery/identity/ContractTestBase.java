package com.fooddelivery.identity;

import com.fooddelivery.identity.controller.AuthController;
import com.fooddelivery.identity.service.AuthService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import com.fooddelivery.common.service.RateLimitingService;

import java.time.Duration;

public abstract class ContractTestBase {

    @BeforeEach
    public void setup() {
        AuthService authService = Mockito.mock(AuthService.class);
        RateLimitingService rateLimitingService = Mockito.mock(RateLimitingService.class);

        // A bare mock returns null from resolveBucket, and AuthController calls tryConsume on the
        // result without a null check -- so every contract test died on a NullPointerException
        // inside the rate-limit guard, before reaching the behaviour under test. Hand it a real
        // in-memory bucket with the same shape the controller asks for, so requests are permitted
        // and the contract exercises the response it is actually about.
        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
                .build();
        Mockito.when(rateLimitingService.resolveBucket(
                        ArgumentMatchers.anyString(),
                        ArgumentMatchers.anyInt(),
                        ArgumentMatchers.anyInt(),
                        ArgumentMatchers.any(Duration.class)))
                .thenReturn(bucket);

        AuthController controller = new AuthController(authService, rateLimitingService);
        RestAssuredMockMvc.standaloneSetup(controller);
    }
}
