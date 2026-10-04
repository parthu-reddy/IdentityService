package com.fooddelivery.identity.service;

import com.fooddelivery.identity.entitlement.service.EntitlementService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DevOtpAccountPolicyTest {
    @Test void anyValidNumberCanUseEverydayDevAutofill() {
        var entitlements=mock(EntitlementService.class);var policy=new DevOtpAccessPolicy(entitlements);
        for (String phone:new String[]{"1234567890","9876543210","8999123456","7999123456","9999123456","8000000001","7000000001","9000000001","1000000001"})
            assertTrue(policy.allows(phone,OtpPurpose.LOGIN));
        verifyNoInteractions(entitlements);assertFalse(SeededOtpAccountPolicy.allows("8999123456",OtpPurpose.LOGIN));
    }
    @Test void administratorAutofillRequiresBothAllowlistAndActiveStaffAssignment() {
        var entitlements=mock(EntitlementService.class);var policy=new DevOtpAccessPolicy(entitlements);
        assertFalse(policy.allows("1000000001",OtpPurpose.ADMIN));
        when(entitlements.administratorWithPhone("1000000001")).thenReturn(true);
        when(entitlements.administratorWithPhone("1000000002")).thenReturn(true);
        assertTrue(policy.allows("1000000001",OtpPurpose.ADMIN));assertTrue(policy.allows("1000000002",OtpPurpose.ADMIN));
        for (String phone:new String[]{"1000000000","1000000003","1234567890","8000000001","7000000001","9000000001","9999123456"})
            assertFalse(policy.allows(phone,OtpPurpose.ADMIN),phone);
    }
    @Test void malformedNumbersAndMissingPurposesAreRejected() {
        var policy=new DevOtpAccessPolicy(mock(EntitlementService.class));
        for (var purpose:OtpPurpose.values()) {
            for (String phone:new String[]{"","899912345","89991234567","abcdefghij","+918999123456","899912345 "}) assertFalse(policy.allows(phone,purpose));
            assertFalse(policy.allows(null,purpose));
        }
        assertFalse(policy.allows("9876543210",null));
    }
    @Test void autofillCannotLoadInProductionEvenWhenEnabled() {
        for (String[] profiles:new String[][]{{"prod"},{"dev","prod"},{"test"}}) {
            context(profiles,"true").run(c -> assertTrue(c.getBeansOfType(DevOtpAccessPolicy.class).isEmpty()));
        }
        context(new String[]{"dev"},"true").run(c -> assertEquals(1,c.getBeansOfType(DevOtpAccessPolicy.class).size()));
        context(new String[]{"dev"},"false").run(c -> assertTrue(c.getBeansOfType(DevOtpAccessPolicy.class).isEmpty()));
    }
    private org.springframework.boot.test.context.runner.ApplicationContextRunner context(String[] profiles,String enabled) {
        return new org.springframework.boot.test.context.runner.ApplicationContextRunner()
            .withUserConfiguration(DevOtpAccessPolicy.class).withBean(EntitlementService.class,()->mock(EntitlementService.class))
            .withInitializer(c -> c.getEnvironment().setActiveProfiles(profiles)).withPropertyValues("dev.otp.enabled="+enabled);
    }
    @Test void parkedRunnerStillOnlySupportsSeededRangesAndTheTwoAdmins() {
        for (String phone:new String[]{"8000000001","8000000504","9000000014","7000000034","1000000002"}) assertTrue(SeededOtpAccountPolicy.allows(phone,OtpPurpose.LOGIN));
        for (String phone:new String[]{"8000000505","9000000015","7000000035","1000000003"}) assertFalse(SeededOtpAccountPolicy.allows(phone,OtpPurpose.LOGIN));
        assertTrue(SeededOtpAccountPolicy.allows("1000000002",OtpPurpose.ADMIN));assertFalse(SeededOtpAccountPolicy.allows("8000000001",OtpPurpose.ADMIN));
    }
}
