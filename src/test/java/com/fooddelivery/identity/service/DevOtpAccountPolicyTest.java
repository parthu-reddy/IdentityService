package com.fooddelivery.identity.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DevOtpAccountPolicyTest {
    @Test
    void anyValidNumberCanUseNonAdminDevAutofill() {
        var policy = new DevOtpAccessPolicy();
        for (var portal : new AuthPortal[] {AuthPortal.CUSTOMER, AuthPortal.DELIVERY, AuthPortal.RESTAURANT}) {
            for (String phone : new String[] {"1234567890", "9876543210", "8999123456", "7999123456", "9999123456",
                    "8000000001", "7000000001", "9000000001", "1000000001"}) {
                assertTrue(policy.allows(phone, portal), portal + " " + phone);
            }
        }
        // The parked runner-secret feature keeps its own seeded-account boundary.
        assertFalse(SeededOtpAccountPolicy.allows("8999123456", AuthPortal.CUSTOMER));
    }

    @Test
    void administratorDevAutofillKeepsItsPhoneAllowlist() {
        var policy = new DevOtpAccessPolicy();
        assertTrue(policy.allows("1000000001", AuthPortal.ADMIN));
        assertTrue(policy.allows("1000000002", AuthPortal.ADMIN));
        for (String phone : new String[] {"1000000000", "1000000003", "1234567890", "9876543210",
                "8000000001", "7000000001", "9000000001", "8999123456", "7999123456", "9999123456"}) {
            assertFalse(policy.allows(phone, AuthPortal.ADMIN), phone);
        }
    }

    @Test
    void malformedNumbersAndMissingPortalsAreRejected() {
        var policy = new DevOtpAccessPolicy();
        for (var portal : AuthPortal.values()) {
            for (String phone : new String[] {"", "899912345", "89991234567", "abcdefghij", "+918999123456", "899912345 "}) {
                assertFalse(policy.allows(phone, portal), portal + " " + phone);
            }
            assertFalse(policy.allows(null, portal));
        }
        assertFalse(policy.allows("9876543210", null));
    }

    @Test
    void autofillPolicyCannotLoadInProductionEvenWithTheFlagEnabled() {
        for (String[] profiles : new String[][] {{"prod"}, {"dev", "prod"}, {"test"}}) {
            new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                    .withUserConfiguration(DevOtpAccessPolicy.class)
                    .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles))
                    .withPropertyValues("dev.otp.enabled=true")
                    .run(context -> assertTrue(context.getBeansOfType(DevOtpAccessPolicy.class).isEmpty()));
        }
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(DevOtpAccessPolicy.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .withPropertyValues("dev.otp.enabled=true")
                .run(context -> assertEquals(1, context.getBeansOfType(DevOtpAccessPolicy.class).size()));
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(DevOtpAccessPolicy.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .withPropertyValues("dev.otp.enabled=false")
                .run(context -> assertTrue(context.getBeansOfType(DevOtpAccessPolicy.class).isEmpty()));
    }

    @Test
    void scenarioAccountsAndTwoProvisionedAdministratorsAreCovered() {
        assertTrue(SeededOtpAccountPolicy.allows("8000000504", AuthPortal.CUSTOMER));
        assertFalse(SeededOtpAccountPolicy.allows("8000000505", AuthPortal.CUSTOMER));
        assertTrue(SeededOtpAccountPolicy.allows("7000000034", AuthPortal.DELIVERY));
        assertFalse(SeededOtpAccountPolicy.allows("7000000035", AuthPortal.DELIVERY));
        assertTrue(SeededOtpAccountPolicy.allows("9000000014", AuthPortal.RESTAURANT));
        assertFalse(SeededOtpAccountPolicy.allows("9000000015", AuthPortal.RESTAURANT));
        assertTrue(SeededOtpAccountPolicy.allows("1000000002", AuthPortal.ADMIN));
        assertFalse(SeededOtpAccountPolicy.allows("1000000003", AuthPortal.ADMIN));
    }
}
