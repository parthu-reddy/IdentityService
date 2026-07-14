package com.fooddelivery.identity.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.identity.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/internal/auth")
@RequiredArgsConstructor
@PreAuthorize("permitAll()")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<String>> initiateLogin(
            @RequestParam String phoneNumber, 
            @RequestHeader("X-Calling-Service") String serviceName) {
            
        authService.initiateLogin(phoneNumber, serviceName);
        return ResponseEntity.ok(ApiResponse.success(null, "OTP sent successfully"));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<String>> verifyOtp(
            @RequestParam String phoneNumber, 
            @RequestParam String otp, 
            @RequestHeader("X-Calling-Service") String serviceName) {
            
        String token = authService.verifyOtp(phoneNumber, otp, serviceName);
        return ResponseEntity.ok(ApiResponse.success(token, "Login successful"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String token) {
        
        // In a real implementation we would blacklist the token here.
        // Since we are using stateless JWT, we'll just return success 
        // to signify that the client can clear its token.
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }
}
