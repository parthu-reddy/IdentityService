package com.fooddelivery.identity.service;

import com.fooddelivery.identity.config.MapsIdentityProperties;
import com.fooddelivery.identity.dto.SessionInfo;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.exception.MapsIdentityException;
import com.fooddelivery.identity.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class MapsCurrentSessionServiceTest {
    final Clock clock = mock(Clock.class);
    final UserRepository users = mock(UserRepository.class);
    final SessionRegistry sessions = mock(SessionRegistry.class);
    final MapsSessionTokens tokens = mock(MapsSessionTokens.class);
    final Instant now = Instant.parse("2026-11-04T00:00:00Z");
    final UUID id = UUID.randomUUID();
    final String sid = UUID.randomUUID().toString();
    final MapsIdentityProperties properties = new MapsIdentityProperties();
    AppUser user; SessionInfo session; MapsCurrentSessionService current;
    @BeforeEach void setup() {
        properties.setAuthority("https://identity.test"); properties.setAudience("maps-editor");
        when(clock.instant()).thenReturn(now);
        when(users.mapsAuthorityIsPrimary()).thenReturn(true);
        when(tokens.verify("server-held-credential", false)).thenReturn(new MapsSessionTokens.Verified(id, sid, 4, now.minusSeconds(10), now.plusSeconds(60)));
        user = AppUser.builder().id(id).isActive(true).entitlementsVersion(4).build();
        session = SessionInfo.builder().sessionId(sid).purpose(OtpPurpose.LOGIN).createdAt(now.minusSeconds(20)).absoluteExpiresAt(now.plusSeconds(600)).build();
        when(users.findById(id)).thenReturn(Optional.of(user)); when(sessions.require(id, sid)).thenReturn(session);
        current = new MapsCurrentSessionService(users, sessions, tokens, properties, clock);
    }
    @Test void completedCurrentDecisionNamesTheHumanAndCapsItsLifetimeWithoutWrites() {
        var result = current.validate("server-held-credential", "n".repeat(43));
        assertEquals(id.toString(), result.get("subject")); assertEquals(sid, result.get("session_id"));
        assertEquals(now.toString(), result.get("checked_at")); assertEquals(now.plusSeconds(1).toString(), result.get("valid_until"));
        assertEquals(java.util.Map.of("mfa", false, "methods", java.util.List.of("otp"), "authenticated_at", session.getCreatedAt().toString()), result.get("authentication"));
        verify(users).mapsAuthorityIsPrimary();verify(users).findById(id); verify(sessions).require(id, sid); verifyNoMoreInteractions(users, sessions);
    }
    @Test void everyRequestObservesCurrentDisableVersionAndMissingAccount() {
        current.validate("server-held-credential", "n".repeat(43)); user.setActive(false);
        assertThrows(MapsIdentityException.class, () -> current.validate("server-held-credential", "n".repeat(43)));
        user.setActive(true); user.setEntitlementsVersion(5);
        assertThrows(MapsIdentityException.class, () -> current.validate("server-held-credential", "n".repeat(43)));
        user.setEntitlementsVersion(3);
        assertThrows(MapsIdentityException.class, () -> current.validate("server-held-credential", "n".repeat(43)));
        when(users.findById(id)).thenReturn(Optional.empty());
        assertThrows(MapsIdentityException.class, () -> current.validate("server-held-credential", "n".repeat(43)));
        verify(users, times(5)).findById(id); verify(sessions, times(1)).require(id, sid);
    }
    @Test void wrongPurposeOrBindingAndExpiryWhileCheckingNeverProduceActive() {
        session.setPurpose(OtpPurpose.ADMIN);
        assertThrows(MapsIdentityException.class, () -> current.validate("server-held-credential", "n".repeat(43)));
        session.setPurpose(OtpPurpose.LOGIN); session.setCreatedAt(now);
        assertThrows(MapsIdentityException.class, () -> current.validate("server-held-credential", "n".repeat(43)));
        session.setCreatedAt(now.minusSeconds(20)); when(clock.instant()).thenReturn(now.plusSeconds(60));
        assertThrows(MapsIdentityException.class, () -> current.validate("server-held-credential", "n".repeat(43)));
    }
    @Test void currentMfaExpiresAtTheOriginalDeadlineAndRequiresTheConfiguredSubject() {
        var challenge = now.minusSeconds(299).minusMillis(750);
        properties.setPublisherSubject(id);
        when(tokens.verify("server-held-credential", false)).thenReturn(
                new MapsSessionTokens.Verified(id, sid, 4, now.minusSeconds(10), now.plusSeconds(60), challenge));
        var active = current.validate("server-held-credential", "n".repeat(43));
        assertEquals(now.plusMillis(250).toString(), active.get("valid_until"));
        assertEquals(java.util.Map.of("mfa", true, "methods", java.util.List.of("otp", "password"),
                "authenticated_at", challenge.toString()), active.get("authentication"));

        properties.setPublisherSubject(UUID.randomUUID());
        assertEquals(false, ((java.util.Map<?, ?>) current.validate("server-held-credential", "n".repeat(43))
                .get("authentication")).get("mfa"));
        properties.setPublisherSubject(id);
        when(clock.instant()).thenReturn(now.plusMillis(250));
        assertEquals(false, ((java.util.Map<?, ?>) current.validate("server-held-credential", "n".repeat(43))
                .get("authentication")).get("mfa"));
    }

    @Test void standbyAuthorityCannotProduceAnActiveDecision() {
        when(users.mapsAuthorityIsPrimary()).thenReturn(false);
        var error=assertThrows(MapsIdentityException.class,()->current.validate("server-held-credential","n".repeat(43)));
        assertEquals(MapsIdentityException.Code.UNAVAILABLE,error.code());verify(users,never()).findById(any());verifyNoInteractions(sessions);
    }
}
