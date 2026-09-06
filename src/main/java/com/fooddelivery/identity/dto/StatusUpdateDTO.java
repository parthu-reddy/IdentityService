package com.fooddelivery.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusUpdateDTO {
    @NotNull(message = "isActive is required")
    private Boolean isActive;
}
