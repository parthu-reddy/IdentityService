package com.fooddelivery.identity.dinein.service;

import com.fooddelivery.common.audit.AuditTrail;
import com.fooddelivery.common.enums.AuditAction;
import com.fooddelivery.identity.dinein.dto.DineInGuestRequests.DineInGuestLinkRequest;
import com.fooddelivery.identity.dinein.dto.DineInGuestRequests.DineInGuestLinked;
import com.fooddelivery.identity.port.CachePort;
import com.fooddelivery.identity.port.EventPublisherPort;
import com.fooddelivery.identity.service.AccountUnavailableException;
import com.fooddelivery.identity.service.UserProvisioner;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A dine-in guest linked to a table by phone alone (owner, 2026-10-31; DineInPhoneAndCounter D1): the account is found or
 * created for the phone, and the phone gets an informational SMS with no code (D2). The guest proves the number is theirs
 * only by signing in to pay in the app; a guest who pays at the restaurant never has to. The three caps are the single
 * authority on how often a phone, a waiter and an outlet may link guests (D3).
 */
@Service
@lombok.RequiredArgsConstructor
public class DineInGuestService {
    static final int PER_PHONE_PER_HOUR = 5;
    static final int PER_STAFF_PER_HOUR = 30;
    static final int PER_OUTLET_PER_HOUR = 120;
    private static final long CAP_WINDOW_MINUTES = 60;
    static final String CAP_KEY = "RATELIMIT:DINE_IN_GUEST:";
    private static final String SUBJECT = "DINE_IN_GUEST";

    private final CachePort cache;
    private final EventPublisherPort events;
    private final UserProvisioner provisioner;
    private final AuditTrail audit;
    private final Clock clock;

    /** A blocked account's refusal commits its DINING_GUEST_REFUSED audit row; the exception still reaches the caller. */
    @Transactional(noRollbackFor = DineInGuestException.class)
    public DineInGuestLinked link(DineInGuestLinkRequest request) {
        cap("OUTLET:" + request.outletId(), PER_OUTLET_PER_HOUR);
        cap("STAFF:" + request.requestedByUserId(), PER_STAFF_PER_HOUR);
        cap("PHONE:" + request.phone(), PER_PHONE_PER_HOUR);
        Instant now = clock.instant();
        UserProvisioner.Provisioned guest;
        try {
            guest = provisioner.findOrCreate(request.phone());
        } catch (AccountUnavailableException unavailable) {
            record(now, request, AuditAction.DINING_GUEST_REFUSED, null, "GUEST_ACCOUNT_UNAVAILABLE");
            throw new DineInGuestException(
                    HttpStatus.FORBIDDEN, "GUEST_ACCOUNT_UNAVAILABLE", "This guest's account cannot be used for orders");
        }
        UUID guestUserId = guest.user().getId();
        events.publishDineInSeated(request.sessionId(), guestUserId, request.phone());
        record(now, request, AuditAction.DINING_GUEST_LINKED, guestUserId, null);
        return new DineInGuestLinked(guestUserId, guest.created(), masked(request.phone()));
    }

    private void cap(String subject, int perHour) {
        Long count = cache.increment(CAP_KEY + subject, CAP_WINDOW_MINUTES);
        if (count == null)
            throw new DineInGuestException(
                    HttpStatus.SERVICE_UNAVAILABLE, "GUEST_LINK_UNAVAILABLE", "Seating guests is temporarily unavailable");
        if (count > perHour)
            throw new DineInGuestException(
                    HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_GUEST_LINKS", "Too many guests seated; try again later");
    }

    /** Ids only (AuditTrail rule): the phone never enters the audit trail, masked or not. */
    private void record(Instant at, DineInGuestLinkRequest request, AuditAction action, UUID guestUserId, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("outletId", request.outletId());
        details.put("sessionId", request.sessionId());
        audit.record(at, request.requestedByUserId(), "USER", action, SUBJECT, guestUserId == null ? request.sessionId() : guestUserId,
                null, reason, details);
    }

    static String masked(String phone) {
        return "******" + phone.substring(phone.length() - 4);
    }
}
