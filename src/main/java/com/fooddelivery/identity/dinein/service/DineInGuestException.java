package com.fooddelivery.identity.dinein.service;

import org.springframework.http.HttpStatus;

/** A refused guest link, with the machine-readable code dining branches on. */
public class DineInGuestException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public DineInGuestException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
