package com.fooddelivery.identity.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.identity.dto.RoleRequestDTO;
import com.fooddelivery.identity.dto.UserDTO;
import com.fooddelivery.identity.service.InternalUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final InternalUserService internalUserService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDTO>> getUser(
            @PathVariable UUID id,
            @RequestHeader("X-Calling-Service") String callingService) {
            
        UserDTO userDTO = internalUserService.getUser(id, callingService);
        return ResponseEntity.ok(ApiResponse.success(userDTO, "User retrieved successfully"));
    }

    @GetMapping("/by-role")
    public ResponseEntity<ApiResponse<List<UserDTO>>> getUsersByRole(
            @RequestParam String role,
            @RequestHeader("X-Calling-Service") String callingService) {
            
        List<UserDTO> users = internalUserService.getUsersByRole(role, callingService);
        return ResponseEntity.ok(ApiResponse.success(users, "Users retrieved successfully"));
    }

    @PostMapping("/{id}/roles")
    public ResponseEntity<ApiResponse<String>> addRole(
            @PathVariable UUID id,
            @Valid @RequestBody RoleRequestDTO request,
            @RequestHeader("X-Calling-Service") String callingService) {
            
        internalUserService.addRoleToUser(id, request.getRoleName(), callingService);
        return ResponseEntity.ok(ApiResponse.success(null, "Role added successfully"));
    }

    @DeleteMapping("/{id}/roles/{roleName}")
    public ResponseEntity<ApiResponse<String>> removeRole(
            @PathVariable UUID id,
            @PathVariable String roleName,
            @RequestHeader("X-Calling-Service") String callingService) {
            
        internalUserService.removeRoleFromUser(id, roleName, callingService);
        return ResponseEntity.ok(ApiResponse.success(null, "Role removed successfully"));
    }
}
