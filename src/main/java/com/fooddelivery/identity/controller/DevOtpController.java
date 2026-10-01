package com.fooddelivery.identity.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.identity.port.CachePort;
import com.fooddelivery.identity.service.AuthPortal;
import com.fooddelivery.identity.service.DevOtpAccessPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Dev Autofill Code uses the real, current OTP for a development login; only administrator numbers are allowlisted. It does
 * not verify the OTP, create a session, provision an account, or assign an application role.
 */
@RestController
@RequestMapping("/api/v1/internal/auth/admin")
@Profile("dev & !prod")
@ConditionalOnProperty(prefix = "dev.otp", name = "enabled", havingValue = "true")
@PreAuthorize("permitAll()")
public class DevOtpController {

    public static final String OTP_LOOKUP_PATH = "/api/v1/internal/auth/admin/otp";

    private final CachePort cachePort;
    private final DevOtpAccessPolicy accessPolicy;

    public DevOtpController(CachePort cachePort, DevOtpAccessPolicy accessPolicy) {
        this.cachePort = cachePort;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping("/otp")
    public ResponseEntity<ApiResponse<String>> getOtp(
            @RequestParam String phoneNumber, @RequestParam String serviceName) {
        AuthPortal portal = AuthPortal.fromCallerService(serviceName);
        if (!accessPolicy.allows(phoneNumber, portal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Dev autofill requires a valid 10-digit number; administrator numbers must be allowlisted");
        }

        String otp = cachePort.get("OTP:" + phoneNumber + ":" + portal.sessionServiceName());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(ApiResponse.success(otp,
                        otp == null ? "No active OTP found for this number" : "OTP retrieved successfully"));
    }
}
