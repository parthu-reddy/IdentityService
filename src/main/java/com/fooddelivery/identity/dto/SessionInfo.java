package com.fooddelivery.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo {
    private String sessionId;
    private String deviceInfo;
    private String os;
    private String browser;
    private long lastActive;
    private String serviceName;
}
