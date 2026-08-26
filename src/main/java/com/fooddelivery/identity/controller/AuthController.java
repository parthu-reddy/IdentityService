package com.fooddelivery.identity.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.identity.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;
import java.util.UUID;
import com.fooddelivery.identity.dto.SessionInfo;

@RestController
@RequestMapping("/api/v1/internal/auth")
@PreAuthorize("permitAll()")
@lombok.extern.slf4j.Slf4j
public class AuthController {
    @java.lang.SuppressWarnings("all")

    private final AuthService authService;
    private final com.fooddelivery.common.service.RateLimitingService rateLimitingService;

    private boolean isRateLimited(String clientKey) {
        if (clientKey == null || clientKey.isBlank() || clientKey.equals("unknown")) return true;
        io.github.bucket4j.Bucket bucket = rateLimitingService.resolveBucket("auth:" + clientKey, 10, 10, java.time.Duration.ofMinutes(1));
        return !bucket.tryConsume(1);
    }

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<String>> initiateLogin(@RequestParam String phoneNumber, @RequestHeader("X-Calling-Service") String serviceName) {
        if (isRateLimited(phoneNumber)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).build();
        }
        authService.initiateLogin(phoneNumber, serviceName);
        return ResponseEntity.ok(ApiResponse.success(null, "OTP sent successfully"));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<String>> verifyOtp(@RequestParam String phoneNumber, @RequestParam String otp, @RequestHeader("X-Calling-Service") String serviceName, @RequestHeader(value = "X-Device-Info", required = false) String deviceInfo, @RequestHeader(value = "X-Device-OS", required = false) String os, @RequestHeader(value = "X-Device-Browser", required = false) String browser, @RequestParam(value = "removeSessionId", required = false) String removeSessionId) {
        if (isRateLimited(phoneNumber)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).build();
        }
        String token = authService.verifyOtp(phoneNumber, otp, serviceName, deviceInfo, os, browser, removeSessionId);
        return ResponseEntity.ok(ApiResponse.success(token, "Login successful"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader(value = "Authorization", required = false) String token, @RequestHeader(value = com.fooddelivery.common.constants.HeaderConstants.HEADER_USER_ID, required = false) String userId, @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        boolean loggedOut = false;
        // Primary: use gateway-injected headers (already validated by the gateway)
        if (userId != null && !userId.isEmpty() && sessionId != null && !sessionId.isEmpty()) {
            authService.removeSession(java.util.UUID.fromString(userId), sessionId);
            loggedOut = true;
        }
        // Fallback: parse the token directly if gateway headers were missing
        if (!loggedOut && token != null && token.startsWith("Bearer ")) {
            try {
                authService.logout(token.substring(7));
            } catch (Exception e) {
                // Log but don't fail — the logout response should always be 200
                log.warn("Token-based logout failed, session may already be removed", e);
            }
        }
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<SessionInfo>>> getActiveSessions(@RequestHeader(value = com.fooddelivery.common.constants.HeaderConstants.HEADER_USER_ID, required = false) String userId, @RequestHeader(value = "X-Calling-Service", required = false) String callingService) {
        if (userId != null && !userId.isEmpty()) {
            List<SessionInfo> sessions = authService.getUserSessions(UUID.fromString(userId));
            return ResponseEntity.ok(ApiResponse.success(sessions, "Sessions retrieved successfully"));
        }
        return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> removeSession(@RequestHeader(value = com.fooddelivery.common.constants.HeaderConstants.HEADER_USER_ID, required = false) String userId, @RequestHeader(value = "X-Calling-Service", required = false) String callingService, @PathVariable String sessionId) {
        if (userId != null && !userId.isEmpty()) {
            authService.removeSession(UUID.fromString(userId), sessionId);
            return ResponseEntity.ok(ApiResponse.success(null, "Session removed successfully"));
        }
        return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<ApiResponse<Void>> removeAllSessions(@RequestHeader(value = com.fooddelivery.common.constants.HeaderConstants.HEADER_USER_ID, required = false) String userId, @RequestHeader(value = "X-Calling-Service", required = false) String callingService) {
        if (userId != null && !userId.isEmpty()) {
            authService.removeAllSessions(UUID.fromString(userId));
            return ResponseEntity.ok(ApiResponse.success(null, "All sessions removed successfully"));
        }
        return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
    }

    @java.lang.SuppressWarnings("all")
    public AuthController(final AuthService authService, final com.fooddelivery.common.service.RateLimitingService rateLimitingService) {
        this.authService = authService;
        this.rateLimitingService = rateLimitingService;
    }
}
