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
import com.fooddelivery.identity.dto.UpdateProfileRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("permitAll()")
@Slf4j
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<Map<String, String>>> getProfile(@RequestHeader("X-User-Id") String userId) {
        AppUser user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "name", user.getName() != null ? user.getName() : "",
            "email", user.getEmail() != null ? user.getEmail() : "",
            "phone", user.getPhoneNumber() != null ? user.getPhoneNumber() : ""
        ), "Profile fetched"));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<String>> updateProfile(
            @RequestHeader("X-User-Id") String userId, 
            @Valid @RequestBody UpdateProfileRequest request) {
        AppUser user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            if (user.getName() != null && !user.getName().trim().isEmpty() && !user.getName().equals(request.getName())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Name cannot be modified once set"));
            }
            user.setName(request.getName());
        }

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            if (user.getEmail() != null && !user.getEmail().trim().isEmpty() && !user.getEmail().equals(request.getEmail())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Email cannot be modified once set"));
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            user.setPhoneNumber(request.getPhone());
        }
        
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success(null, "Profile updated"));
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
