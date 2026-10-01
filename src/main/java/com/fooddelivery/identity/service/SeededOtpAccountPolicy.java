package com.fooddelivery.identity.service;

/**
 * The shared, server-owned account boundary for development OTP inspection. This pure policy
 * does not register an endpoint or enable either the Dev autofill or the E2E runner facility.
 */
public final class SeededOtpAccountPolicy {

    private static final long CUSTOMER_FIRST = 8_000_000_001L;
    private static final long CUSTOMER_LAST = 8_000_000_504L;
    private static final long RESTAURANT_FIRST = 9_000_000_001L;
    private static final long RESTAURANT_LAST = 9_000_000_014L;
    private static final long DELIVERY_FIRST = 7_000_000_001L;
    private static final long DELIVERY_LAST = 7_000_000_034L;
    private static final long ADMIN_FIRST = 1_000_000_001L;
    private static final long ADMIN_LAST = 1_000_000_002L;

    private SeededOtpAccountPolicy() {
    }

    public static boolean allows(String phoneNumber, AuthPortal portal) {
        if (phoneNumber == null || !phoneNumber.matches("\\d{10}") || portal == null) {
            return false;
        }

        long phone = Long.parseLong(phoneNumber);
        return switch (portal) {
            case CUSTOMER -> inRange(phone, CUSTOMER_FIRST, CUSTOMER_LAST);
            case RESTAURANT -> inRange(phone, RESTAURANT_FIRST, RESTAURANT_LAST);
            case DELIVERY -> inRange(phone, DELIVERY_FIRST, DELIVERY_LAST);
            case ADMIN -> inRange(phone, ADMIN_FIRST, ADMIN_LAST);
        };
    }

    private static boolean inRange(long value, long first, long last) {
        return value >= first && value <= last;
    }
}
