package com.fooddelivery.identity.service;

/** Reserved disposable phone pools, used only by the Dev autofill facility. Never seed these. */
public final class RegistrationOtpAccountPolicy {
    private RegistrationOtpAccountPolicy() { }

    public static boolean allows(String phoneNumber, AuthPortal portal) {
        if (phoneNumber == null || portal == null || !phoneNumber.matches("[789]999[0-9]{6}")) {
            return false;
        }
        return switch (portal) {
            case CUSTOMER -> phoneNumber.startsWith("8999");
            case DELIVERY -> phoneNumber.startsWith("7999");
            case RESTAURANT -> phoneNumber.startsWith("9999");
            case ADMIN -> false;
        };
    }
}
