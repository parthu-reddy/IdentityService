package com.fooddelivery.identity.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.identity.port.CachePort;
import com.fooddelivery.identity.service.AuthPortal;
import com.fooddelivery.identity.service.E2eRunnerSecretVerifier;
import com.fooddelivery.identity.service.E2eSeededAccountAllowlist;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/internal/e2e/auth")
@Profile("e2e")
@ConditionalOnProperty(prefix = "e2e.otp", name = "enabled", havingValue = "true")
@PreAuthorize("permitAll()")
public class E2eOtpController {
    private final CachePort cachePort;
    private final E2eRunnerSecretVerifier runnerSecretVerifier;
    private final E2eSeededAccountAllowlist seededAccountAllowlist;

    /**
     * Test-harness-only OTP lookup. The browser never calls this endpoint: the Java E2E process
     * sends its runner credential after the browser has requested an OTP through the normal flow.
     */
    @GetMapping("/otp")
    public ResponseEntity<ApiResponse<String>> getOtp(
            @RequestHeader(value = E2eRunnerSecretVerifier.HEADER_NAME, required = false)
                    List<String> runnerSecrets,
            @RequestParam @jakarta.validation.constraints.Pattern(regexp = "\\d{10}") String phoneNumber,
            @RequestParam(defaultValue = "CUSTOMER") String serviceName) {
        if (!runnerSecretVerifier.matches(runnerSecrets)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "E2E runner credentials are required");
        }

        AuthPortal portal = AuthPortal.fromCallerService(serviceName);
        if (!seededAccountAllowlist.allows(phoneNumber, portal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This endpoint supports seeded E2E accounts only");
        }

        String cacheKey = "OTP:" + phoneNumber + ":" + portal.sessionServiceName();
        String otp = cachePort.get(cacheKey);
        if (otp != null) {
            return ResponseEntity.ok(ApiResponse.success(otp, "OTP retrieved successfully"));
        }
        return ResponseEntity.ok(ApiResponse.success(null, "No active OTP found for this number"));
    }

    public E2eOtpController(CachePort cachePort, E2eRunnerSecretVerifier runnerSecretVerifier,
            E2eSeededAccountAllowlist seededAccountAllowlist) {
        this.cachePort = cachePort;
        this.runnerSecretVerifier = runnerSecretVerifier;
        this.seededAccountAllowlist = seededAccountAllowlist;
    }
}
