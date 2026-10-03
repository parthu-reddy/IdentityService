package com.fooddelivery.identity.organisation.service;

@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.GONE)
public class InvitationExpiredException extends RuntimeException {
    public InvitationExpiredException() { super("This invitation has expired. Ask for a new invitation."); }
}
