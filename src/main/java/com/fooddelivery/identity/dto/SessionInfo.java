package com.fooddelivery.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@lombok.Builder
@lombok.Data


public class SessionInfo {
    @NotNull
    private String sessionId;
    @NotNull
    private String deviceInfo;
    @NotNull
    private String os;
    @NotNull
    private String browser;
    @Schema(requiredMode = RequiredMode.REQUIRED)
    private long lastActive;
    @NotNull
    private String serviceName;


}
