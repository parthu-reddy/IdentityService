package com.fooddelivery.identity.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.common.dto.identity.RoleRequestDTO;
import com.fooddelivery.common.enums.RoleName;
import com.fooddelivery.identity.dto.UserDTO;
import com.fooddelivery.identity.service.InternalUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * User administration, on the path the gateway actually lets a browser reach.
 *
 * <p>These five endpoints were on {@code InternalUserController} at {@code /api/v1/internal/users/**}.
 * {@code GlobalJwtAuthFilter} 403s external requests to {@code /api/v1/internal/**} unless the path
 * begins {@code /api/v1/internal/admin/} — so the whole admin user-management screen was refused at
 * the gateway even though {@code admin-identity-routes} routed it. Found 2026-09-09 auditing every
 * browser path against the gateway's routes, RBAC rules and the services' specs.
 *
 * <p>The split follows the convention every other admin screen already uses
 * ({@code /internal/admin/orders}, {@code /internal/admin/refunds}, {@code /internal/admin/ledger}):
 * the admin surface is separate from the service-to-service one. {@code InternalUserController}
 * keeps only {@code GET /{id}}, its single Feign caller.
 */
@RestController
@RequestMapping("/api/v1/internal/admin/users")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class AdminUserController {

    private final InternalUserService internalUserService;

    /**
     * The one endpoint here with a service-to-service caller: ReviewsService reaches it through
     * IdentityServiceClient while serving an authenticated end user, and FeignSecurityInterceptor
     * propagates that user's own roles -- nothing anywhere mints a SERVICE role. So this admits any
     * authenticated principal rather than a specific role, which is what it already relied on.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDTO>> getUser(@PathVariable UUID id, @RequestHeader("X-Calling-Service") String callingService) {
        UserDTO userDTO = internalUserService.getUser(id, callingService);
        return ResponseEntity.ok(ApiResponse.success(userDTO, "User retrieved successfully"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<com.fooddelivery.common.dto.PageResponseDto<UserDTO>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<UserDTO> users = internalUserService.getAllUsers(pageable);
        return ResponseEntity.ok(ApiResponse.success(com.fooddelivery.common.dto.PageResponseDto.of(users), "All users retrieved successfully"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{userId}/status")
    public ResponseEntity<ApiResponse<String>> updateUserStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody com.fooddelivery.identity.dto.StatusUpdateDTO status) {
        boolean isActive = status.getIsActive() != null ? status.getIsActive() : true;
        internalUserService.updateUserStatus(userId, isActive);
        return ResponseEntity.ok(ApiResponse.success(null, "User status updated successfully"));
    }

    /** Enumerates every user holding a role; an admin capability, not a service one. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/by-role")
    public ResponseEntity<ApiResponse<com.fooddelivery.common.dto.PageResponseDto<UserDTO>>> getUsersByRole(
            @RequestParam RoleName role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestHeader("X-Calling-Service") String callingService) {
        
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<UserDTO> users = internalUserService.getUsersByRole(role, callingService, pageable);
        return ResponseEntity.ok(ApiResponse.success(com.fooddelivery.common.dto.PageResponseDto.of(users), "Users retrieved successfully"));
    }

    /**
     * Privilege GRANT. Until 2026-08-28 this controller carried no authorization at all, while
     * IdentityMcpService.addRole(userId, roleName) exposed it as an MCP tool taking both the target
     * user and the role from the caller -- a self-service route to ROLE_ADMIN.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/roles")
    public ResponseEntity<ApiResponse<String>> addRole(@PathVariable UUID id, @Valid @RequestBody RoleRequestDTO request, @RequestHeader("X-Calling-Service") String callingService) {
        internalUserService.addRoleToUser(id, RoleName.valueOf(request.getRoleName()), callingService);
        return ResponseEntity.ok(ApiResponse.success(null, "Role added successfully"));
    }

    /** Privilege REVOKE; same reasoning as the grant above. */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}/roles/{roleName}")
    public ResponseEntity<ApiResponse<String>> removeRole(@PathVariable UUID id, @PathVariable RoleName roleName, @RequestHeader("X-Calling-Service") String callingService) {
        internalUserService.removeRoleFromUser(id, roleName, callingService);
        return ResponseEntity.ok(ApiResponse.success(null, "Role removed successfully"));
    }
}
