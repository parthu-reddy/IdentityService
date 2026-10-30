package com.fooddelivery.identity.dinein;

import com.fooddelivery.common.audit.AuditTrail;
import com.fooddelivery.common.enums.AuditAction;
import com.fooddelivery.common.exception.ResourceNotFoundException;
import com.fooddelivery.identity.dinein.dto.DineInConsentRequests.*;
import com.fooddelivery.identity.dinein.service.DineInConsentException;
import com.fooddelivery.identity.dinein.service.DineInConsentService;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.port.CachePort;
import com.fooddelivery.identity.port.EventPublisherPort;
import com.fooddelivery.identity.repository.UserRepository;
import com.fooddelivery.identity.service.UserProvisioner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real service, provisioner and in-memory store; the clock moves by hand. Never contacts Redis or a database. */
class DineInConsentServiceTest {
    final String phone = "8999123456";
    final UUID outlet = UUID.randomUUID(), session = UUID.randomUUID(), waiter = UUID.randomUUID();
    final InMemoryDineInConsentStore store = new InMemoryDineInConsentStore();
    final Map<String, Long> counters = new HashMap<>();
    final CachePort cache = mock(CachePort.class);
    final EventPublisherPort events = mock(EventPublisherPort.class);
    final UserRepository users = mock(UserRepository.class);
    final AuditTrail audit = mock(AuditTrail.class);
    final Clock clock = mock(Clock.class);
    Instant now = Instant.parse("2026-10-29T12:00:00Z");
    DineInConsentService service;

    @BeforeEach void setup() {
        when(clock.instant()).thenAnswer(i -> now);
        when(cache.increment(anyString(), anyLong())).thenAnswer(i -> counters.merge(i.getArgument(0), 1L, Long::sum));
        service = new DineInConsentService(store, cache, events, new UserProvisioner(users), audit, clock);
    }

    DineInConsentRequest request(String phone, UUID outlet, UUID waiter) {
        return new DineInConsentRequest(phone, outlet, session, waiter);
    }

    /** Requests a consent and returns {consentId, the code the guest was sent}. */
    String[] requested() {
        var r = service.request(request(phone, outlet, waiter));
        var code = ArgumentCaptor.forClass(String.class);
        verify(events, atLeastOnce()).publishDineInConsentCode(eq(r.consentId()), eq(phone), code.capture());
        return new String[] {r.consentId().toString(), code.getValue()};
    }

    AppUser existing(boolean active) {
        var user = AppUser.builder().id(UUID.randomUUID()).phoneNumber(phone).isActive(active).build();
        when(users.findByPhoneNumber(phone)).thenReturn(Optional.of(user));
        when(users.lockById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    DineInConsentException refused(String code, Runnable work) {
        var ex = assertThrows(DineInConsentException.class, work::run);
        assertEquals(code, ex.code());
        return ex;
    }

    static String other(String code) {
        return code.equals("000000") ? "000001" : "000000";
    }

    @Test void requestSendsTheCodeToThePhoneAndAuditsIdsOnly() {
        var r = service.request(request(phone, outlet, waiter));
        assertEquals("******3456", r.maskedPhone());
        assertEquals(now.plus(Duration.ofMinutes(10)), r.expiresAt());
        verify(events).publishDineInConsentCode(eq(r.consentId()), eq(phone), matches("[0-9]{6}"));
        assertEquals(Duration.ofMinutes(20), store.retention.get(r.consentId()));
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq(now), eq(waiter), eq("USER"), eq(AuditAction.DINING_CONSENT_REQUESTED), eq("DINE_IN_CONSENT"),
                eq(r.consentId()), isNull(), isNull(), details.capture());
        assertEquals(Map.of("outletId", outlet, "sessionId", session), details.getValue());
    }

    @Test void rightCodeCreatesTheGuestAccountOnceAndIsSingleUse() {
        var saved = AppUser.builder().id(UUID.randomUUID()).phoneNumber(phone).isActive(true).build();
        when(users.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(users.saveAndFlush(any())).thenReturn(saved);
        when(users.lockById(saved.getId())).thenReturn(Optional.of(saved));
        var c = requested();
        var verified = service.verify(UUID.fromString(c[0]), c[1]);
        assertEquals(saved.getId(), verified.userId());
        assertTrue(verified.newAccount());
        verify(audit).record(eq(now), eq(waiter), eq("USER"), eq(AuditAction.DINING_CONSENT_VERIFIED), eq("DINE_IN_CONSENT"),
                eq(UUID.fromString(c[0])), isNull(), isNull(), eq(Map.of("outletId", outlet, "sessionId", session, "guestUserId", saved.getId())));
        assertThrows(ResourceNotFoundException.class, () -> service.verify(UUID.fromString(c[0]), c[1]));
        verify(users, times(1)).saveAndFlush(any());
    }

    @Test void existingGuestIsReusedNotRecreated() {
        var user = existing(true);
        var c = requested();
        var verified = service.verify(UUID.fromString(c[0]), c[1]);
        assertEquals(user.getId(), verified.userId());
        assertFalse(verified.newAccount());
        verify(users, never()).saveAndFlush(any());
    }

    @Test void loginCodeCannotConsent() {
        existing(true);
        var c = requested();
        String loginCode = other(c[1]);
        when(cache.get("OTP:" + phone + ":LOGIN")).thenReturn(loginCode); // the guest's own live sign-in code
        var ex = refused("CONSENT_CODE_INVALID", () -> service.verify(UUID.fromString(c[0]), loginCode));
        assertEquals(422, ex.status().value());
        verify(cache, never()).get(anyString());
        verify(users, never()).lockById(any());
        assertTrue(store.consents.containsKey(UUID.fromString(c[0])), "a wrong code does not use the consent up");
    }

    @Test void fifthWrongCodeKillsTheConsent() {
        var c = requested();
        UUID id = UUID.fromString(c[0]);
        for (int attempt = 1; attempt <= 5; attempt++) {
            var ex = refused("CONSENT_CODE_INVALID", () -> service.verify(id, other(c[1])));
            assertEquals(attempt == 5 ? "Wrong code; no attempts left, request a new one"
                    : "Wrong code; " + (5 - attempt) + " attempts left", ex.getMessage());
        }
        var dead = refused("CONSENT_EXPIRED", () -> service.verify(id, c[1]));
        assertEquals(410, dead.status().value());
        verify(users, never()).findByPhoneNumber(any());
        verify(audit, times(6)).record(any(), eq(waiter), eq("USER"), eq(AuditAction.DINING_CONSENT_FAILED), eq("DINE_IN_CONSENT"),
                eq(id), isNull(), anyString(), anyMap());
    }

    @Test void expiredConsentAnswersGoneAndCreatesNoAccount() {
        var c = requested();
        now = now.plus(Duration.ofMinutes(10));
        var ex = refused("CONSENT_EXPIRED", () -> service.verify(UUID.fromString(c[0]), c[1]));
        assertEquals(410, ex.status().value());
        assertEquals(0L, store.attempts.get(UUID.fromString(c[0])), "an expired consent spends no attempt");
        verify(users, never()).findByPhoneNumber(any());
    }

    @Test void unknownConsentIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.verify(UUID.randomUUID(), "123456"));
    }

    @Test void perPhoneCapRefusesTheSixthRequest() {
        for (int i = 0; i < 5; i++) service.request(request(phone, UUID.randomUUID(), UUID.randomUUID()));
        var ex = refused("TOO_MANY_CONSENT_REQUESTS", () -> service.request(request(phone, UUID.randomUUID(), UUID.randomUUID())));
        assertEquals(429, ex.status().value());
        verify(events, times(5)).publishDineInConsentCode(any(), any(), any());
        service.request(request("8999123457", UUID.randomUUID(), UUID.randomUUID())); // another phone is unaffected
    }

    @Test void perStaffCapRefusesTheThirtyFirstRequest() {
        for (int i = 0; i < 30; i++) service.request(request(String.format("70000%05d", i), UUID.randomUUID(), waiter));
        var ex = refused("TOO_MANY_CONSENT_REQUESTS", () -> service.request(request("7000000999", UUID.randomUUID(), waiter)));
        assertEquals(429, ex.status().value());
        verify(events, times(30)).publishDineInConsentCode(any(), any(), any());
        service.request(request("7000000999", UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test void perOutletCapRefusesTheHundredTwentyFirstRequest() {
        for (int i = 0; i < 120; i++) service.request(request(String.format("70000%05d", i), outlet, UUID.randomUUID()));
        var ex = refused("TOO_MANY_CONSENT_REQUESTS", () -> service.request(request("7000000999", outlet, UUID.randomUUID())));
        assertEquals(429, ex.status().value());
        verify(events, times(120)).publishDineInConsentCode(any(), any(), any());
        service.request(request("7000000999", UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test void capsCountPerHourWindow() {
        service.request(request(phone, outlet, waiter));
        verify(cache).increment("RATELIMIT:DINE_IN:PHONE:" + phone, 60L);
        verify(cache).increment("RATELIMIT:DINE_IN:STAFF:" + waiter, 60L);
        verify(cache).increment("RATELIMIT:DINE_IN:OUTLET:" + outlet, 60L);
    }

    @Test void blockedGuestIsRefused() {
        var user = existing(false);
        var c = requested();
        var ex = refused("GUEST_ACCOUNT_UNAVAILABLE", () -> service.verify(UUID.fromString(c[0]), c[1]));
        assertEquals(403, ex.status().value());
        assertFalse(user.isActive());
        verify(users, never()).saveAndFlush(any());
        verify(audit).record(any(), eq(waiter), eq("USER"), eq(AuditAction.DINING_CONSENT_FAILED), eq("DINE_IN_CONSENT"),
                eq(UUID.fromString(c[0])), isNull(), eq("GUEST_ACCOUNT_UNAVAILABLE"), anyMap());
    }

    @Test void unavailableCacheRefusesInsteadOfSkippingTheCaps() {
        when(cache.increment(anyString(), anyLong())).thenReturn(null);
        var ex = refused("CONSENT_UNAVAILABLE", () -> service.request(request(phone, outlet, waiter)));
        assertEquals(503, ex.status().value());
        verifyNoInteractions(events);
    }
}
