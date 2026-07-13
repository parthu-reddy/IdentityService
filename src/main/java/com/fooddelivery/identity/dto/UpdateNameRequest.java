package com.fooddelivery.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateNameRequest {
    @NotBlank(message = "Name cannot be empty")
    private String name;
}
