package com.fooddelivery.identity.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A conservative full-window retry hint for a refused Maps OTP send. No key or phone is retained. */
public final class MapsOtpRateLimitException extends ResponseStatusException {
    private final int retryAfterSeconds;
    public MapsOtpRateLimitException(long seconds) {
        super(HttpStatus.TOO_MANY_REQUESTS);
        if (seconds < 1 || seconds > 86400) throw new IllegalArgumentException("Invalid Maps OTP retry bound");
        retryAfterSeconds = (int) seconds;
    }
    public int retryAfterSeconds() { return retryAfterSeconds; }
    @Override public HttpHeaders getHeaders() {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Integer.toString(retryAfterSeconds));
        return HttpHeaders.readOnlyHttpHeaders(headers);
    }
}
