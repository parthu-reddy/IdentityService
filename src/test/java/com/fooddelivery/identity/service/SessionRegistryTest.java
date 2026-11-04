package com.fooddelivery.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.identity.dto.SessionInfo;
import com.fooddelivery.identity.port.CachePort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the real persisted-session decoder; no Redis/DB, OTPs or credentials. */
class SessionRegistryTest {
    private static final Instant NOW = Instant.parse("2026-11-04T00:00:00Z");
    private final UUID userId = UUID.randomUUID();
    private final CachePort cache = mock(CachePort.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Clock clock = mock(Clock.class);
    private SessionRegistry sessions;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        sessions = new SessionRegistry(cache, mapper, clock);
    }

    private String row(String id, String purpose, String created, String expiry) {
        return "{\"sessionId\":\"" + id + "\",\"purpose\":\"" + purpose
                + "\",\"createdAt\":\"" + created + "\",\"absoluteExpiresAt\":\"" + expiry + "\"}";
    }

    private String row(String id) {
        return row(id, "LOGIN", NOW.minusSeconds(60).toString(), NOW.plusSeconds(60).toString());
    }

    private void stored(String json) { when(cache.get("USER_SESSIONS:" + userId)).thenReturn(json); }

    private void refuses(int status, Runnable action) {
        var error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(status, error.getStatusCode().value());
        assertNull(error.getCause(), "Never retain cache payloads or dependency messages in the public error");
    }

    @Test void missingStateIsInactiveAndNeverReconstructed() {
        assertTrue(sessions.list(userId).isEmpty());
        refuses(401, () -> sessions.require(userId, "owned"));
        verify(cache, never()).put(anyString(), anyString(), anyLong());
    }

    @Test void fullRegistryIsValidatedBeforeExpiredRowsAreRemoved() {
        stored("[" + row("owned") + "," + row("expired", "LOGIN", NOW.minusSeconds(120).toString(), NOW.toString()) + "]");
        assertEquals(List.of("owned"), sessions.list(userId).stream().map(SessionInfo::getSessionId).toList());
        stored("[" + row("owned") + "," + row("owned", "LOGIN", NOW.minusSeconds(120).toString(), NOW.toString()) + "]");
        refuses(503, () -> sessions.require(userId, "owned"));
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "null", "{}", "[null]", "[] []", "[{}]"})
    void malformedRegistryIsUnavailable(String json) {
        stored(json);
        refuses(503, () -> sessions.list(userId));
    }

    @Test void duplicatePropertiesAreRefusedRegardlessOfOrderingOrEquality() {
        for (String duplicate : List.of(
                row("owned").replace("\"sessionId\":\"owned\"", "\"sessionId\":\"other\",\"sessionId\":\"owned\""),
                row("owned").replace("\"purpose\":\"LOGIN\"", "\"purpose\":\"ADMIN\",\"purpose\":\"LOGIN\""),
                row("owned").replace("\"purpose\":\"LOGIN\"", "\"purpose\":\"LOGIN\",\"purpose\":\"LOGIN\""))) {
            stored("[" + duplicate + "]");
            refuses(503, () -> sessions.require(userId, "owned"));
        }
    }

    @Test void invalidLaterRowCannotHideBehindAnActiveFirstMatch() {
        for (String invalid : List.of("null", "{}", row(""), row(" "), row("owned"),
                row("other").replace("\"LOGIN\"", "0"),
                row("other").replace("\"LOGIN\"", "\"UNKNOWN\""),
                row("other").replace("\"LOGIN\"", "\"login\""),
                row("other").replace("\"sessionId\":\"other\"", "\"sessionId\":123"),
                row("other", "LOGIN", NOW.toString(), NOW.toString()),
                row("other", "LOGIN", NOW.plusSeconds(120).toString(), NOW.plusSeconds(60).toString()))) {
            stored("[" + row("owned") + "," + invalid + "]");
            refuses(503, () -> sessions.require(userId, "owned"));
        }
    }

    @Test void registrySizeIsBoundedInUtf8Bytes() {
        stored("[" + row("owned").replace("}", ",\"deviceInfo\":\"" + "é".repeat(8100) + "\"}") + "]");
        refuses(503, () -> sessions.list(userId));
        stored("[" + row("owned").replace("}", ",\"deviceInfo\":\"" + "x".repeat(15000) + "\"}") + "]");
        assertEquals(1, sessions.list(userId).size());
    }

    @Test void exactByteLimitIsAllowedButOneMoreByteIsUnavailable() {
        String base = "[" + row("owned") + "]";
        String exact = base + " ".repeat(16_000 - base.length());
        stored(exact);
        assertEquals(1, sessions.list(userId).size());
        stored(exact + " ");
        refuses(503, () -> sessions.list(userId));
    }

    @Test void invalidActivityCannotBeCoercedOrOverflow() {
        for (String activity : List.of("null", "\"1\"", "true", "-1", "1.5", "9223372036854775808")) {
            stored("[" + row("owned").replace("}", ",\"lastActive\":" + activity + "}") + "]");
            refuses(503, () -> sessions.list(userId));
        }
    }

    @Test void strictDecodingDoesNotChangeTheSharedMapper() throws Exception {
        stored("[" + row("owned") + "]");
        sessions.list(userId);
        assertEquals(2, mapper.readTree("{\"value\":1,\"value\":2}").path("value").intValue());
    }

    @Test void passiveLookupNeverTouchesActivityOrWritesAndKeepsActorIsolation() {
        stored("[" + row("owned") + "]");
        assertEquals("owned", sessions.require(userId, "owned").getSessionId());
        refuses(401, () -> sessions.require(UUID.randomUUID(), "owned"));
        verify(cache, never()).put(anyString(), anyString(), anyLong());
        verify(cache, never()).delete(anyString());
        verify(cache, never()).increment(anyString(), anyLong());
    }

    @Test void aPositiveReadIsNeverCachedAcrossRequests() {
        when(cache.get("USER_SESSIONS:" + userId)).thenReturn("[" + row("owned") + "]", "[]");
        assertNotNull(sessions.require(userId, "owned"));
        refuses(401, () -> sessions.require(userId, "owned"));
        verify(cache, times(2)).get("USER_SESSIONS:" + userId);
    }

    @Test void blacklistIsCurrentAndDependencyFailuresAreUnavailable() {
        stored("[" + row("owned") + "]");
        when(cache.get("BLACKLIST:SESSION:owned")).thenReturn(null, "true");
        assertNotNull(sessions.require(userId, "owned"));
        refuses(401, () -> sessions.require(userId, "owned"));
        when(cache.get("BLACKLIST:SESSION:owned")).thenThrow(new IllegalStateException("private dependency detail"));
        refuses(503, () -> sessions.require(userId, "owned"));
        when(cache.get("USER_SESSIONS:" + userId)).thenThrow(new IllegalStateException("private dependency detail"));
        refuses(503, () -> sessions.list(userId));
    }

    @Test void exactExpiryAndExpiryWhileReadingAreInactive() {
        stored("[" + row("owned") + "]");
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        refuses(401, () -> sessions.require(userId, "owned"));
        when(clock.instant()).thenReturn(NOW, NOW.plusSeconds(60));
        refuses(401, () -> sessions.require(userId, "owned"));
    }

    @Test void blankSessionIdentifiersDoNotReadTheAuthority() {
        for (String id : new String[]{null, "", " "}) refuses(401, () -> sessions.require(userId, id));
        refuses(401, () -> sessions.require(null, "owned"));
        refuses(401, () -> sessions.list(null));
        verifyNoInteractions(cache);
    }

    @Test void bothPurposesAndPreviouslySerializedNumericInstantsRemainReadable() throws Exception {
        var ordinary = SessionInfo.builder().sessionId("ordinary").purpose(OtpPurpose.LOGIN)
                .createdAt(NOW.minusSeconds(10)).absoluteExpiresAt(NOW.plusSeconds(60)).build();
        var admin = SessionInfo.builder().sessionId("admin").purpose(OtpPurpose.ADMIN)
                .createdAt(NOW.minusSeconds(10)).absoluteExpiresAt(NOW.plusSeconds(60)).build();
        stored(mapper.writeValueAsString(List.of(ordinary, admin)));
        assertEquals(2, sessions.list(userId).size());
        assertEquals(OtpPurpose.ADMIN, sessions.require(userId, "admin").getPurpose());
    }
}
