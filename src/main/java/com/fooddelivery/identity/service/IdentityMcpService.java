package com.fooddelivery.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.identity.controller.*;
import com.fooddelivery.identity.dto.UpdateProfileRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import com.fooddelivery.common.enums.RoleName;
import com.fooddelivery.common.dto.identity.RoleRequestDTO;
import java.util.UUID;

@Service
@lombok.extern.slf4j.Slf4j
public class IdentityMcpService {
    @java.lang.SuppressWarnings("all")

    private final AdminUserController adminUserController;
    private final AuthController authController;
    private final AdminOtpController adminOtpController;
    private final UserController userController;
    private final ObjectMapper objectMapper;

    public IdentityMcpService(AdminUserController adminUserController, AuthController authController, AdminOtpController adminOtpController, UserController userController, ObjectMapper objectMapper) {
        this.adminUserController = adminUserController;
        this.authController = authController;
        this.adminOtpController = adminOtpController;
        this.userController = userController;
        this.objectMapper = objectMapper;
    }

    // InternalUserController
    @Tool(description = "Get user by ID. Provide userId.")
    public String getUser(String userId) {
        try {
            return objectMapper.writeValueAsString(adminUserController.getUser(UUID.fromString(userId), "MCP_SERVICE").getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Get users by role. Provide roleName.")
    public String getUsersByRole(String roleName) {
        try {
            return objectMapper.writeValueAsString(adminUserController.getUsersByRole(RoleName.valueOf(roleName), 0, 50, "MCP_SERVICE").getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Add role to user. Provide userId and roleName.")
    public String addRole(String userId, String roleName) {
        try {
            RoleRequestDTO roleRequestDTO = new RoleRequestDTO();
            roleRequestDTO.setRoleName(roleName);
            return objectMapper.writeValueAsString(adminUserController.addRole(UUID.fromString(userId), roleRequestDTO, "MCP_SERVICE").getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Remove role from user. Provide userId and roleName.")
    public String removeRole(String userId, String roleName) {
        try {
            return objectMapper.writeValueAsString(adminUserController.removeRole(UUID.fromString(userId), RoleName.valueOf(roleName), "MCP_SERVICE").getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // AuthController
    @Tool(description = "Initiate login via OTP. Provide phoneNumber and serviceName.")
    public String initiateLogin(String phoneNumber, String serviceName) {
        try {
            return objectMapper.writeValueAsString(authController.initiateLogin(phoneNumber, serviceName).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Verify OTP for login. Provide phoneNumber, otp, serviceName, deviceInfo, os, browser, removeSessionId (nullable).")
    public String verifyOtp(String phoneNumber, String otp, String serviceName, String deviceInfo, String os, String browser, String removeSessionId) {
        try {
            return objectMapper.writeValueAsString(authController.verifyOtp(phoneNumber, otp, serviceName, deviceInfo, os, browser, removeSessionId).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Logout user. Provide userId and sessionId (or token).")
    public String logout(String token, String userId, String sessionId) {
        try {
            return objectMapper.writeValueAsString(authController.logout(token, userId, sessionId).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Get active sessions for user. Provide userId.")
    public String getActiveSessions(String userId) {
        try {
            return objectMapper.writeValueAsString(authController.getActiveSessions(userId, null).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Remove a session for user. Provide userId and sessionId.")
    public String removeSession(String userId, String sessionId) {
        try {
            return objectMapper.writeValueAsString(authController.removeSession(userId, null, sessionId).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Remove all sessions for user. Provide userId.")
    public String removeAllSessions(String userId) {
        try {
            return objectMapper.writeValueAsString(authController.removeAllSessions(userId, null).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // AdminOtpController
    @Tool(description = "Get current OTP for a user number (Dev only). Provide phoneNumber and serviceName.")
    public String getOtp(String phoneNumber, String serviceName) {
        try {
            return objectMapper.writeValueAsString(adminOtpController.getOtp(phoneNumber, serviceName).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // UserController
    @Tool(description = "Get user profile. Provide userId.")
    public String getProfile(String userId) {
        try {
            return objectMapper.writeValueAsString(userController.getProfile(userId).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Update user profile. Provide userId and JSON string of UpdateProfileRequest (name, email, phone).")
    public String updateProfile(String userId, String requestJson) {
        try {
            UpdateProfileRequest req = objectMapper.readValue(requestJson, UpdateProfileRequest.class);
            return objectMapper.writeValueAsString(userController.updateProfile(userId, req).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
