package com.fooddelivery.identity.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A conservative full-window retry hint for refused Maps rate-limited requests (OTP, session, refresh).
 * No key or identity is retained.
 */
public class MapsRateLimitException extends ResponseStatusException {
    private final int retryAfterSeconds;

    public MapsRateLimitException(long seconds) {
        super(HttpStatus.TOO_MANY_REQUESTS);
        if (seconds < 1 || seconds > 86400) {
            throw new IllegalArgumentException("Invalid Maps retry bound");
        }
        this.retryAfterSeconds = (int) seconds;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public HttpHeaders getHeaders() {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Integer.toString(retryAfterSeconds));
        return HttpHeaders.readOnlyHttpHeaders(headers);
    }
}
