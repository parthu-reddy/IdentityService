package com.fooddelivery.identity.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.identity.dto.AuthRequests.*;
import com.fooddelivery.identity.dto.SessionInfo;
import com.fooddelivery.identity.organisation.service.OrganisationService;
import com.fooddelivery.identity.service.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import java.util.*;

@RestController @RequestMapping("/api/v1/auth") @lombok.RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    private final ObjectProvider<DevOtpAccessPolicy> devPolicy;

    @PostMapping("/otp") @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<Void>> initiateLogin(@Valid @RequestBody OtpRequest request) {
        auth.initiateLogin(request.phoneNumber());
        var builder=ResponseEntity.ok().cacheControl(CacheControl.noStore());
        var policy=devPolicy.getIfAvailable();
        if (policy!=null && policy.allows(request.phoneNumber(),OtpPurpose.LOGIN)) builder.header(DevOtpAccessPolicy.AVAILABILITY_HEADER,"true");
        return builder.body(ApiResponse.success(null,"Code sent"));
    }
    @PostMapping("/session") @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<SessionResponse>> createSession(@Valid @RequestBody SessionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(auth.createSession(request),"Signed in"));
    }
    @PostMapping("/session/refresh") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SessionResponse>> refresh(Authentication caller) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(auth.refresh(OrganisationService.userId(caller),sid(caller)),"Session refreshed"));
    }
    @PostMapping("/admin-session/otp") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> initiateAdminOtp(Authentication caller) {
        auth.initiateAdminOtp(OrganisationService.userId(caller),sid(caller));
        var builder=ResponseEntity.ok().cacheControl(CacheControl.noStore());
        var policy=devPolicy.getIfAvailable();
        if (policy!=null && caller.getDetails() instanceof Map<?,?> details
                && policy.allows(Objects.toString(details.get("phone"),null),OtpPurpose.ADMIN)) builder.header(DevOtpAccessPolicy.AVAILABILITY_HEADER,"true");
        return builder.body(ApiResponse.success(null,"Administrator code sent"));
    }
    @PostMapping("/admin-session") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SessionResponse>> createAdminSession(Authentication caller,@Valid @RequestBody AdminSessionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(auth.createAdminSession(OrganisationService.userId(caller),sid(caller),request),"Administrator session issued"));
    }
    @PostMapping("/logout") @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> logout(Authentication caller) {
        auth.logout(OrganisationService.userId(caller),sid(caller));return ApiResponse.success(null,"Signed out");
    }
    @GetMapping("/sessions") @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<SessionInfo>> getActiveSessions(Authentication caller) {
        return ApiResponse.success(auth.getUserSessions(OrganisationService.userId(caller)),"Active sessions");
    }
    @DeleteMapping("/sessions/{sessionId}") @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> removeSession(Authentication caller,@PathVariable UUID sessionId) {
        auth.removeSession(OrganisationService.userId(caller),sessionId.toString());return ApiResponse.success(null,"Session removed");
    }
    @DeleteMapping("/sessions") @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> removeAllSessions(Authentication caller) {
        auth.removeAllSessions(OrganisationService.userId(caller));return ApiResponse.success(null,"Sessions removed");
    }
    private static String sid(Authentication caller) {
        // SecurityContextFilter includes the session in its verified mesh signature.
        if (caller!=null && caller.getDetails() instanceof Map<?,?> details && details.get("sessionId") instanceof String sid && !sid.isBlank()) return sid;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"A person session is required");
    }
}
