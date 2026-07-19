package com.fooddelivery.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleRequestDTO {
    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "^[A-Za-z0-9_]+$")
    private String roleName;
}
