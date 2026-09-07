package com.fooddelivery.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@lombok.Data


public class UpdateProfileRequest {
    @NotBlank(message = "Name cannot be blank")
    @Size(max = 100)
    private String name;
    @Email(message = "Invalid email format")
    @Size(max = 255)
    private String email;
    @Size(max = 20)
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone format")
    private String phone;

}
