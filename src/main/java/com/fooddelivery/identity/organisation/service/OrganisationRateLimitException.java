package com.fooddelivery.identity.organisation.service;

public class OrganisationRateLimitException extends RuntimeException {
    private final long retryAfter;
    public OrganisationRateLimitException(long retryAfter) { super("Too many organisation requests. Please try again later."); this.retryAfter=retryAfter; }
    public long retryAfter() { return retryAfter; }
}
