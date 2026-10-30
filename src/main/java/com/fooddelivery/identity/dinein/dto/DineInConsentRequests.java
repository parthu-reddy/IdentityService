package com.fooddelivery.identity.dinein.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/** Internal API of guest consent; the caller is restaurant-service dining, acting for the waiter it names. */
public final class DineInConsentRequests {
    private DineInConsentRequests() {}

    public record DineInConsentRequest(
            @NotNull @Pattern(regexp = "[0-9]{10}", message = "phone must be 10 digits") String phone,
            @NotNull UUID outletId,
            @NotNull UUID sessionId,
            @NotNull UUID requestedByUserId) {}

    public record DineInConsentVerification(
            @NotNull @Pattern(regexp = "[0-9]{6}", message = "code must be 6 digits") String code) {}

    public record DineInConsentRequested(UUID consentId, Instant expiresAt, String maskedPhone) {}

    public record DineInConsentVerified(UUID userId, boolean newAccount) {}
}
