package com.fooddelivery.identity.dto;

import jakarta.validation.constraints.*;

public final class AuthRequests {
    private AuthRequests() { }
    public record OtpRequest(@NotBlank @Pattern(regexp="[0-9]{10}") String phoneNumber) { }
    public record SessionRequest(@NotBlank @Pattern(regexp="[0-9]{10}") String phoneNumber,
        @NotBlank @Pattern(regexp="[0-9]{6}") String otp,
        @Size(max=200) String deviceInfo,@Size(max=80) String os,@Size(max=80) String browser,
        @Pattern(regexp="[0-9a-fA-F-]{36}") String replaceSessionId) { }
    public record AdminSessionRequest(@NotBlank @Pattern(regexp="[0-9]{6}") String otp,
        @Pattern(regexp="[0-9a-fA-F-]{36}") String replaceSessionId) { }
    public record SessionResponse(String token,boolean newUser) { }
}
