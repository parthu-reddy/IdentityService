package com.fooddelivery.identity.dinein.service;

import com.fooddelivery.common.audit.AuditTrail;
import com.fooddelivery.common.enums.AuditAction;
import com.fooddelivery.common.exception.ResourceNotFoundException;
import com.fooddelivery.identity.dinein.dto.DineInConsentRequests.*;
import com.fooddelivery.identity.dinein.port.DineInConsent;
import com.fooddelivery.identity.dinein.port.DineInConsentStore;
import com.fooddelivery.identity.port.CachePort;
import com.fooddelivery.identity.port.EventPublisherPort;
import com.fooddelivery.identity.service.AccountUnavailableException;
import com.fooddelivery.identity.service.OtpPurpose;
import com.fooddelivery.identity.service.UserProvisioner;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A dine-in guest's consent to orders a waiter places for their phone (OtpPurpose.DINE_IN). The code is SMSed to the
 * guest, who reads it to the waiter; verifying it finds or creates the guest's account. A DINE_IN code lives only in
 * the consent store, so it can never sign anyone in, and a LOGIN code never matches a consent.
 */
@Service
@lombok.RequiredArgsConstructor
public class DineInConsentService {
    static final int PER_PHONE_PER_HOUR = 5;
    static final int PER_STAFF_PER_HOUR = 30;
    static final int PER_OUTLET_PER_HOUR = 120;
    static final int MAX_ATTEMPTS = 5;
    static final Duration CONSENT_TTL = Duration.ofMinutes(10);
    /** Kept past expiry so a late verification answers 410 CONSENT_EXPIRED rather than 404. */
    static final Duration RETENTION = CONSENT_TTL.multipliedBy(2);
    private static final long CAP_WINDOW_MINUTES = 60;
    private static final String SUBJECT = "DINE_IN_CONSENT";
    private static final String CAP_KEY = "RATELIMIT:" + OtpPurpose.DINE_IN.name() + ":";

    private final DineInConsentStore store;
    private final CachePort cache;
    private final EventPublisherPort events;
    private final UserProvisioner provisioner;
    private final AuditTrail audit;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public DineInConsentRequested request(DineInConsentRequest request) {
        cap("OUTLET:" + request.outletId(), PER_OUTLET_PER_HOUR);
        cap("STAFF:" + request.requestedByUserId(), PER_STAFF_PER_HOUR);
        cap("PHONE:" + request.phone(), PER_PHONE_PER_HOUR);
        Instant requestedAt = clock.instant();
        var consent =
                new DineInConsent(
                        UUID.randomUUID(),
                        request.phone(),
                        request.outletId(),
                        request.sessionId(),
                        request.requestedByUserId(),
                        String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)),
                        requestedAt.plus(CONSENT_TTL));
        store.save(consent, RETENTION);
        events.publishDineInConsentCode(consent.consentId(), consent.phone(), consent.code());
        record(requestedAt, consent, AuditAction.DINING_CONSENT_REQUESTED, null, Map.of());
        return new DineInConsentRequested(consent.consentId(), consent.expiresAt(), masked(consent.phone()));
    }

    /** Refusals commit their DINING_CONSENT_FAILED audit row; the exception still reaches the caller. */
    @Transactional(noRollbackFor = DineInConsentException.class)
    public DineInConsentVerified verify(UUID consentId, String code) {
        var consent =
                store.find(consentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Dine-in consent not found"));
        var now = clock.instant();
        if (!now.isBefore(consent.expiresAt()))
            throw refused(now, consent, HttpStatus.GONE, "CONSENT_EXPIRED", "The code has expired; request a new one");
        long attempts = store.incrementAttempts(consentId);
        if (attempts < 0) throw new ResourceNotFoundException("Dine-in consent not found");
        if (attempts > MAX_ATTEMPTS)
            throw refused(now, consent, HttpStatus.GONE, "CONSENT_EXPIRED", "Too many wrong codes; request a new one");
        boolean matches = codeMatches(consent.code(), code);
        if (!matches)
            throw refused(
                    now,
                    consent,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "CONSENT_CODE_INVALID",
                    attempts == MAX_ATTEMPTS
                            ? "Wrong code; no attempts left, request a new one"
                            : "Wrong code; " + (MAX_ATTEMPTS - attempts) + " attempts left");
        if (!store.consume(consentId)) throw new ResourceNotFoundException("Dine-in consent not found");
        UserProvisioner.Provisioned guest;
        try {
            guest = provisioner.findOrCreate(consent.phone());
        } catch (AccountUnavailableException unavailable) {
            throw refused(
                    now,
                    consent,
                    HttpStatus.FORBIDDEN,
                    "GUEST_ACCOUNT_UNAVAILABLE",
                    "This guest's account cannot be used for orders");
        }
        record(
                now,
                consent,
                AuditAction.DINING_CONSENT_VERIFIED,
                null,
                Map.of("guestUserId", guest.user().getId()));
        return new DineInConsentVerified(guest.user().getId(), guest.created());
    }

    private void cap(String subject, int perHour) {
        Long count = cache.increment(CAP_KEY + subject, CAP_WINDOW_MINUTES);
        if (count == null)
            throw new DineInConsentException(
                    HttpStatus.SERVICE_UNAVAILABLE, "CONSENT_UNAVAILABLE", "Guest consent is temporarily unavailable");
        if (count > perHour)
            throw new DineInConsentException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "TOO_MANY_CONSENT_REQUESTS",
                    "Too many consent requests; try again later");
    }

    private DineInConsentException refused(
            Instant now, DineInConsent consent, HttpStatus status, String code, String message) {
        record(now, consent, AuditAction.DINING_CONSENT_FAILED, code, Map.of());
        return new DineInConsentException(status, code, message);
    }

    /** Ids only (AuditTrail rule): the phone never enters the audit trail, masked or not. */
    private void record(
            Instant at, DineInConsent consent, AuditAction action, String reason, Map<String, Object> extra) {
        var details = new java.util.HashMap<String, Object>(extra);
        details.put("outletId", consent.outletId());
        details.put("sessionId", consent.sessionId());
        audit.record(
                at, consent.requestedByUserId(), "USER", action, SUBJECT, consent.consentId(), null, reason, details);
    }

    private static boolean codeMatches(String expected, String given) {
        return given != null
                && MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    static String masked(String phone) {
        return "******" + phone.substring(phone.length() - 4);
    }
}
