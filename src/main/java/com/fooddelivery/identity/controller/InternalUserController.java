package com.fooddelivery.identity.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.identity.dto.RoleRequestDTO;
import com.fooddelivery.identity.dto.UserDTO;
import com.fooddelivery.identity.service.InternalUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import com.fooddelivery.common.enums.RoleName;
import java.util.Map;
import jakarta.validation.Valid;


@RestController
@RequestMapping("/api/v1/internal/users")
@lombok.extern.slf4j.Slf4j
public class InternalUserController {
    @java.lang.SuppressWarnings("all")

    private final InternalUserService internalUserService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDTO>> getUser(@PathVariable UUID id, @RequestHeader("X-Calling-Service") String callingService) {
        UserDTO userDTO = internalUserService.getUser(id, callingService);
        return ResponseEntity.ok(ApiResponse.success(userDTO, "User retrieved successfully"));
    }

    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<UserDTO>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<UserDTO> users = internalUserService.getAllUsers(pageable);
        return ResponseEntity.ok(ApiResponse.success(users, "All users retrieved successfully"));
    }

    @PutMapping("/admin/{userId}/status")
    public ResponseEntity<ApiResponse<String>> updateUserStatus(
            @PathVariable UUID userId,
            @RequestBody Map<String, Boolean> status) {
        boolean isActive = status.getOrDefault("isActive", true);
        internalUserService.updateUserStatus(userId, isActive);
        return ResponseEntity.ok(ApiResponse.success(null, "User status updated successfully"));
    }

    @GetMapping("/by-role")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<UserDTO>>> getUsersByRole(
            @RequestParam RoleName role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestHeader("X-Calling-Service") String callingService) {
        
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<UserDTO> users = internalUserService.getUsersByRole(role, callingService, pageable);
        return ResponseEntity.ok(ApiResponse.success(users, "Users retrieved successfully"));
    }

    @PostMapping("/{id}/roles")
    public ResponseEntity<ApiResponse<String>> addRole(@PathVariable UUID id, @Valid @RequestBody RoleRequestDTO request, @RequestHeader("X-Calling-Service") String callingService) {
        internalUserService.addRoleToUser(id, request.getRoleName(), callingService);
        return ResponseEntity.ok(ApiResponse.success(null, "Role added successfully"));
    }

    @DeleteMapping("/{id}/roles/{roleName}")
    public ResponseEntity<ApiResponse<String>> removeRole(@PathVariable UUID id, @PathVariable RoleName roleName, @RequestHeader("X-Calling-Service") String callingService) {
        internalUserService.removeRoleFromUser(id, roleName, callingService);
        return ResponseEntity.ok(ApiResponse.success(null, "Role removed successfully"));
    }

    @java.lang.SuppressWarnings("all")
    public InternalUserController(final InternalUserService internalUserService) {
        this.internalUserService = internalUserService;
    }
}
