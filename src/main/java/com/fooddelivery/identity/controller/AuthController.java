package com.fooddelivery.identity.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.identity.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<String>> initiateLogin(@RequestParam String phoneNumber, @RequestParam(defaultValue = "ROLE_CUSTOMER") String role) {
        authService.initiateLogin(phoneNumber, role);
        return ResponseEntity.ok(ApiResponse.success(null, "OTP sent successfully"));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<String>> verifyOtp(@RequestParam String phoneNumber, @RequestParam String otp, @RequestParam(defaultValue = "ROLE_CUSTOMER") String role) {
        String token = authService.verifyOtp(phoneNumber, otp, role);
        return ResponseEntity.ok(ApiResponse.success(token, "Login successful"));
    }
}
