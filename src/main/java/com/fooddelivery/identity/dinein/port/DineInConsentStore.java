package com.fooddelivery.identity.dinein.port;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface DineInConsentStore {
    void save(DineInConsent consent, Duration retention);

    Optional<DineInConsent> find(UUID consentId);

    /** Atomically counts one verification attempt; -1 when the consent is gone. */
    long incrementAttempts(UUID consentId);

    /** Removes the consent; true only for the one caller that removed it (a code is used once). */
    boolean consume(UUID consentId);
}
