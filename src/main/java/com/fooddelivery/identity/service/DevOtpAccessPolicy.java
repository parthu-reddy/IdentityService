package com.fooddelivery.identity.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Enabled only for the explicitly configured Dev autofill flow, never alongside prod. */
@Component
@Profile("dev & !prod")
@ConditionalOnProperty(prefix = "dev.otp", name = "enabled", havingValue = "true")
public final class DevOtpAccessPolicy {

    public static final String AVAILABILITY_HEADER = "X-Dev-OTP-Available";

    public boolean allows(String phoneNumber, AuthPortal portal) {
        return SeededOtpAccountPolicy.allows(phoneNumber, portal)
                || RegistrationOtpAccountPolicy.allows(phoneNumber, portal);
    }
}
