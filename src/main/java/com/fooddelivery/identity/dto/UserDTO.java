package com.fooddelivery.identity.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import com.fooddelivery.common.enums.RoleName;@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@lombok.Builder
@lombok.Data


public class UserDTO {
    @NotNull
    private UUID id;
    @NotNull
    private String phoneNumber;
    @NotNull
    private List<RoleName> roles;
    private boolean isActive;

}
