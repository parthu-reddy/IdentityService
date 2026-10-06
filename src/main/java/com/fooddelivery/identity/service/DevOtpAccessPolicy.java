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

    private final com.fooddelivery.identity.entitlement.service.EntitlementService entitlements;

    public DevOtpAccessPolicy(
            com.fooddelivery.identity.entitlement.service.EntitlementService entitlements) {
        this.entitlements = entitlements;
    }

    public boolean allows(String phoneNumber, OtpPurpose purpose) {
        if (phoneNumber == null || !phoneNumber.matches("[0-9]{10}") || purpose == null) {
            return false;
        }
        if (purpose == OtpPurpose.LOGIN) return true;
        if (!SeededOtpAccountPolicy.allows(phoneNumber, purpose)) return false;
        return entitlements.administratorWithPhone(phoneNumber);
    }
}
