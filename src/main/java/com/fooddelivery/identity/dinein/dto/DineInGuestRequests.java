package com.fooddelivery.identity.dinein.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/** Internal API of the dine-in guest link; the caller is restaurant-service dining, acting for the waiter it names. */
public final class DineInGuestRequests {
    private DineInGuestRequests() {}

    public record DineInGuestLinkRequest(
            @NotNull @Pattern(regexp = "[0-9]{10}", message = "phone must be 10 digits") String phone,
            @NotNull UUID outletId,
            @NotNull UUID sessionId,
            @NotNull UUID requestedByUserId) {}

    /** {@code maskedPhone} is {@code ******1234}: the phone never travels back in full. */
    public record DineInGuestLinked(UUID userId, boolean newAccount, String maskedPhone) {}
}
