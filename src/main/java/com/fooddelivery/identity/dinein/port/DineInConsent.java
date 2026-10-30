package com.fooddelivery.identity.dinein.port;

import java.time.Instant;
import java.util.UUID;

/**
 * One guest consent awaiting its code. Lives in Redis only; the phone is kept (not a hash) because verify finds or
 * creates the guest's account from it, and the request is gone ten minutes later.
 */
public record DineInConsent(
        UUID consentId,
        String phone,
        UUID outletId,
        UUID sessionId,
        UUID requestedByUserId,
        String code,
        Instant expiresAt) {}
