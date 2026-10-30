package com.fooddelivery.identity.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The account exists but is deactivated. Sign-in answers 401 with it; a dine-in consent maps it to its own refusal. */
public class AccountUnavailableException extends ResponseStatusException {
    public AccountUnavailableException() {
        super(HttpStatus.UNAUTHORIZED, "Account is unavailable");
    }
}
