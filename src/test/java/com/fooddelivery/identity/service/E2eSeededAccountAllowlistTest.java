package com.fooddelivery.identity.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Guards the parked runner-secret /internal/e2e/auth/otp harness, not browser Dev Autofill Code.
 * DevOtpAccountPolicyTest proves arbitrary valid non-admin numbers work in the browser flow.
 */
class E2eSeededAccountAllowlistTest {

    private final E2eSeededAccountAllowlist allowlist = new E2eSeededAccountAllowlist();

    @Test
    void runnerSecretHarnessAcceptsOnlyTheConfiguredSeedRangesForTheirPortal() {
        assertTrue(allowlist.allows("8000000001", OtpPurpose.LOGIN));
        assertTrue(allowlist.allows("8000000500", OtpPurpose.LOGIN));
        assertTrue(allowlist.allows("9000000001", OtpPurpose.LOGIN));
        assertTrue(allowlist.allows("9000000010", OtpPurpose.LOGIN));
        assertTrue(allowlist.allows("7000000001", OtpPurpose.LOGIN));
        assertTrue(allowlist.allows("7000000030", OtpPurpose.LOGIN));
        assertTrue(allowlist.allows("1000000001", OtpPurpose.ADMIN));

        assertFalse(allowlist.allows("8000000505", OtpPurpose.LOGIN));
        assertFalse(allowlist.allows("9000000015", OtpPurpose.LOGIN));
        assertFalse(allowlist.allows("7000000035", OtpPurpose.LOGIN));
        assertFalse(allowlist.allows("1000000003", OtpPurpose.ADMIN));
        assertFalse(allowlist.allows("8000000001", OtpPurpose.ADMIN));
    }
}
