package com.fooddelivery.identity.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;
import java.util.UUID;
import jakarta.validation.Valid;
import com.fooddelivery.identity.dto.UpdateNameRequest;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("permitAll()")
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<Map<String, String>>> getProfile(@RequestHeader("X-User-Id") String userId) {
        AppUser user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "name", user.getName() != null ? user.getName() : "",
            "phone", user.getPhoneNumber() != null ? user.getPhoneNumber() : ""
        ), "Profile fetched"));
    }

    @PutMapping("/profile/name")
    public ResponseEntity<ApiResponse<String>> updateName(
            @RequestHeader("X-User-Id") String userId, 
            @Valid @RequestBody UpdateNameRequest request) {
        AppUser user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setName(request.getName());
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success(null, "Name updated"));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<String>> handleValidationException(org.springframework.web.bind.MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getDefaultMessage())
            .findFirst()
            .orElse("Validation error");
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }
}
