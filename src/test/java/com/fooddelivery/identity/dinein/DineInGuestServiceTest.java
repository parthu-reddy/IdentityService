package com.fooddelivery.identity.dinein;

import com.fooddelivery.common.audit.AuditTrail;
import com.fooddelivery.common.enums.AuditAction;
import com.fooddelivery.identity.dinein.dto.DineInGuestRequests.DineInGuestLinkRequest;
import com.fooddelivery.identity.dinein.service.DineInGuestException;
import com.fooddelivery.identity.dinein.service.DineInGuestService;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.port.CachePort;
import com.fooddelivery.identity.port.EventPublisherPort;
import com.fooddelivery.identity.repository.UserRepository;
import com.fooddelivery.identity.service.UserProvisioner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real service and provisioner; the cache counts in a map. Never contacts Redis or a database. */
class DineInGuestServiceTest {
    final String phone = "8999123456";
    final UUID outlet = UUID.randomUUID(), session = UUID.randomUUID(), waiter = UUID.randomUUID();
    final Map<String, Long> counters = new HashMap<>();
    final CachePort cache = mock(CachePort.class);
    final EventPublisherPort events = mock(EventPublisherPort.class);
    final UserRepository users = mock(UserRepository.class);
    final AuditTrail audit = mock(AuditTrail.class);
    final Instant now = Instant.parse("2026-10-31T12:00:00Z");
    DineInGuestService service;

    @BeforeEach void setup() {
        when(cache.increment(anyString(), anyLong())).thenAnswer(i -> counters.merge(i.getArgument(0), 1L, Long::sum));
        service = new DineInGuestService(cache, events, new UserProvisioner(users), audit, Clock.fixed(now, ZoneOffset.UTC));
    }

    DineInGuestLinkRequest request(String phone, UUID outlet, UUID waiter) {
        return new DineInGuestLinkRequest(phone, outlet, session, waiter);
    }

    AppUser existing(String phone, boolean active) {
        var user = AppUser.builder().id(UUID.randomUUID()).phoneNumber(phone).isActive(active).build();
        when(users.findByPhoneNumber(phone)).thenReturn(Optional.of(user));
        when(users.lockById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    DineInGuestException refused(String code, Runnable work) {
        var ex = assertThrows(DineInGuestException.class, work::run);
        assertEquals(code, ex.code());
        return ex;
    }

    @Test void aNewPhoneGetsAnAccountTheSeatedSmsAndAnIdsOnlyAudit() {
        var saved = AppUser.builder().id(UUID.randomUUID()).phoneNumber(phone).isActive(true).build();
        when(users.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(users.saveAndFlush(any())).thenReturn(saved);
        when(users.lockById(saved.getId())).thenReturn(Optional.of(saved));
        var linked = service.link(request(phone, outlet, waiter));
        assertEquals(saved.getId(), linked.userId());
        assertTrue(linked.newAccount());
        assertEquals("******3456", linked.maskedPhone());
        verify(events).publishDineInSeated(session, saved.getId(), phone);
        verify(audit).record(eq(now), eq(waiter), eq("USER"), eq(AuditAction.DINING_GUEST_LINKED), eq("DINE_IN_GUEST"),
                eq(saved.getId()), isNull(), isNull(), eq(Map.of("outletId", outlet, "sessionId", session)));
    }

    @Test void aKnownPhoneIsReusedNotRecreated() {
        var user = existing(phone, true);
        var linked = service.link(request(phone, outlet, waiter));
        assertEquals(user.getId(), linked.userId());
        assertFalse(linked.newAccount());
        verify(users, never()).saveAndFlush(any());
    }

    @Test void aBlockedAccountIsRefusedAuditedAndToldNothing() {
        var user = existing(phone, false);
        var ex = refused("GUEST_ACCOUNT_UNAVAILABLE", () -> service.link(request(phone, outlet, waiter)));
        assertEquals(403, ex.status().value());
        assertFalse(user.isActive());
        verifyNoInteractions(events);
        verify(audit).record(eq(now), eq(waiter), eq("USER"), eq(AuditAction.DINING_GUEST_REFUSED), eq("DINE_IN_GUEST"),
                eq(session), isNull(), eq("GUEST_ACCOUNT_UNAVAILABLE"), eq(Map.of("outletId", outlet, "sessionId", session)));
    }

    @Test void theAuditNeverCarriesThePhone() {
        existing(phone, true);
        service.link(request(phone, outlet, waiter));
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(any(), any(), any(), any(), any(), any(), any(), any(), details.capture());
        assertFalse(details.getValue().toString().contains("3456"));
    }

    @Test void perPhoneCapRefusesTheSixthLink() {
        existing(phone, true);
        for (int i = 0; i < 5; i++) service.link(request(phone, UUID.randomUUID(), UUID.randomUUID()));
        var ex = refused("TOO_MANY_GUEST_LINKS", () -> service.link(request(phone, UUID.randomUUID(), UUID.randomUUID())));
        assertEquals(429, ex.status().value());
        verify(events, times(5)).publishDineInSeated(any(), any(), any());
        existing("8999123457", true);
        service.link(request("8999123457", UUID.randomUUID(), UUID.randomUUID())); // another phone is unaffected
    }

    @Test void perStaffCapRefusesTheThirtyFirstLink() {
        for (int i = 0; i < 31; i++) existing(String.format("70000%05d", i), true);
        for (int i = 0; i < 30; i++) service.link(request(String.format("70000%05d", i), UUID.randomUUID(), waiter));
        var ex = refused("TOO_MANY_GUEST_LINKS", () -> service.link(request("7000000030", UUID.randomUUID(), waiter)));
        assertEquals(429, ex.status().value());
        service.link(request("7000000030", UUID.randomUUID(), UUID.randomUUID())); // another waiter is unaffected
    }

    @Test void perOutletCapRefusesTheHundredTwentyFirstLink() {
        for (int i = 0; i < 121; i++) existing(String.format("70000%05d", i), true);
        for (int i = 0; i < 120; i++) service.link(request(String.format("70000%05d", i), outlet, UUID.randomUUID()));
        var ex = refused("TOO_MANY_GUEST_LINKS", () -> service.link(request("7000000120", outlet, UUID.randomUUID())));
        assertEquals(429, ex.status().value());
        service.link(request("7000000120", UUID.randomUUID(), UUID.randomUUID())); // another outlet is unaffected
    }

    @Test void capsCountPerHourOutletThenStaffThenPhoneBeforeAnyAccountIsTouched() {
        existing(phone, true);
        service.link(request(phone, outlet, waiter));
        InOrder order = inOrder(cache, users);
        order.verify(cache).increment("RATELIMIT:DINE_IN_GUEST:OUTLET:" + outlet, 60L);
        order.verify(cache).increment("RATELIMIT:DINE_IN_GUEST:STAFF:" + waiter, 60L);
        order.verify(cache).increment("RATELIMIT:DINE_IN_GUEST:PHONE:" + phone, 60L);
        order.verify(users).findByPhoneNumber(phone);
    }

    @Test void anUnavailableCacheRefusesInsteadOfSkippingTheCaps() {
        when(cache.increment(anyString(), anyLong())).thenReturn(null);
        var ex = refused("GUEST_LINK_UNAVAILABLE", () -> service.link(request(phone, outlet, waiter)));
        assertEquals(503, ex.status().value());
        verifyNoInteractions(events, users);
    }
}
