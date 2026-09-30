package com.fooddelivery.identity.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.common.dto.identity.RoleRequestDTO;
import com.fooddelivery.common.enums.RoleName;
import com.fooddelivery.identity.dto.UserDTO;
import com.fooddelivery.identity.service.InternalUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
 * the administrative user-management surface is separate from public authentication endpoints.
 */
@RestController
@RequestMapping("/api/v1/internal/admin/users")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class AdminUserController {

    private final InternalUserService internalUserService;

    /** Administrator detail lookup, including every currently assigned portal role. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDTO>> getUser(@PathVariable UUID id) {
        UserDTO userDTO = internalUserService.getUserForAdmin(id);
        return ResponseEntity.ok(ApiResponse.success(userDTO, "User retrieved successfully"));
    }

    /** Support finds a user by the number they sign in with (10 digits). 404 when nobody does. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/by-phone")
    public ResponseEntity<ApiResponse<UserDTO>> getUserByPhone(@RequestParam String phone) {
        return ResponseEntity.ok(ApiResponse.success(internalUserService.getUserByPhone(phone), "User retrieved successfully"));
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
            @RequestParam(defaultValue = "50") int size) {
        
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<UserDTO> users = internalUserService.getUsersByRole(role, pageable);
        return ResponseEntity.ok(ApiResponse.success(com.fooddelivery.common.dto.PageResponseDto.of(users), "Users retrieved successfully"));
    }

    /** Server maps the requested role to its portal; callers cannot choose a role's service scope. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/roles")
    public ResponseEntity<ApiResponse<String>> addRole(@PathVariable UUID id, @Valid @RequestBody RoleRequestDTO request) {
        internalUserService.addRoleToUser(id, RoleName.valueOf(request.getRoleName()));
        return ResponseEntity.ok(ApiResponse.success(null, "Role added successfully"));
    }

    /** Privilege REVOKE; same reasoning as the grant above. */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}/roles/{roleName}")
    public ResponseEntity<ApiResponse<String>> removeRole(@PathVariable UUID id, @PathVariable RoleName roleName,
                                                           Authentication authentication) {
        internalUserService.removeRoleFromUser(id, roleName, authenticatedUserId(authentication));
        return ResponseEntity.ok(ApiResponse.success(null, "Role removed successfully"));
    }

    private UUID authenticatedUserId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only an authenticated administrator may change roles");
        }
    }
}
