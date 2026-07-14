package com.fooddelivery.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @NotBlank(message = "Name cannot be blank")
    private String name;
    
    @Email(message = "Invalid email format")
    private String email;

    private String phone;
}
