package com.fooddelivery.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for GET /api/v1/users/profile.
 * <p>
 * This is the single source of truth for the profile response shape.
 * Adding a new field here automatically exposes it in the API and the
 * generated OpenAPI schema — no risk of silently missing a key like
 * the old {@code Map.of(...)} approach.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponseDto {
    private String id;
    private String name;
    private String email;
    private String phone;
}
