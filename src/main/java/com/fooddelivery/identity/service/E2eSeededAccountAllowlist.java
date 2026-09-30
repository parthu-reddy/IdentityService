package com.fooddelivery.identity.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Limits OTP inspection to the accounts deliberately seeded for browser E2E coverage.
 */
@Component
@Profile("e2e")
@ConditionalOnProperty(prefix = "e2e.otp", name = "enabled", havingValue = "true")
public final class E2eSeededAccountAllowlist {

    public boolean allows(String phoneNumber, AuthPortal portal) {
        return SeededOtpAccountPolicy.allows(phoneNumber, portal);
    }
}
