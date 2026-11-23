package com.fooddelivery.identity.exception;

/**
 * Specific rate-limit retry hint for Maps OTP send requests.
 */
public final class MapsOtpRateLimitException extends MapsRateLimitException {
    public MapsOtpRateLimitException(long seconds) {
        super(seconds);
    }
}
