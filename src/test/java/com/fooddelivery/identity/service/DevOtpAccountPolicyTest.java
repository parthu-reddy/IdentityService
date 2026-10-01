package com.fooddelivery.identity.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DevOtpAccountPolicyTest {
    @Test
    void disposablePoolsAreRoleBoundAndSeparateFromSeededRunnerAccess() {
        var policy = new DevOtpAccessPolicy();
        for (var portal : AuthPortal.values()) {
            assertEquals(portal == AuthPortal.CUSTOMER, policy.allows("8999123456", portal));
            assertEquals(portal == AuthPortal.DELIVERY, policy.allows("7999123456", portal));
            assertEquals(portal == AuthPortal.RESTAURANT, policy.allows("9999123456", portal));
            assertFalse(SeededOtpAccountPolicy.allows("8999123456", portal));
            assertFalse(policy.allows("1234567890", portal));
            assertFalse(policy.allows("899912345", portal));
        }
        assertFalse(policy.allows(null, AuthPortal.CUSTOMER));
        assertFalse(policy.allows("8999123456", null));
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
