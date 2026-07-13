package com.fooddelivery.identity.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.identity.port.CachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/internal/auth/admin")
@RequiredArgsConstructor
@Profile("dev")
@PreAuthorize("permitAll()")
public class AdminOtpController {

    private final CachePort cachePort;

    @GetMapping("/otp")
    public ResponseEntity<ApiResponse<String>> getOtp(
            @RequestParam String phoneNumber,
            @RequestParam(defaultValue = "CUSTOMER") String serviceName) {
            
        String normalizedServiceName = serviceName != null ? serviceName.toLowerCase() : "customer";
        String cacheKey = "OTP:" + phoneNumber + ":" + normalizedServiceName;
        String otp = cachePort.get(cacheKey);
        
        if (otp != null) {
            return ResponseEntity.ok(ApiResponse.success(otp, "OTP retrieved successfully"));
        } else {
            return ResponseEntity.ok(ApiResponse.success(null, "No active OTP found for this number"));
        }
    }
}
