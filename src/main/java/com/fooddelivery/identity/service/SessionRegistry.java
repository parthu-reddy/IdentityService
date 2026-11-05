package com.fooddelivery.identity.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.identity.dto.SessionInfo;
import com.fooddelivery.identity.exception.MaxSessionsReachedException;
import com.fooddelivery.identity.port.CachePort;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Call mutations while holding the person's DB lock; no process-local synchronization. */
@Service
@lombok.RequiredArgsConstructor
public class SessionRegistry {
    // Same persisted-value budget as ApiGateway's PersonSessionVerifier (UTF-8 bytes).
    private static final int MAX_REGISTRY_BYTES = 16_000;
    private final CachePort cache;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Value("${identity.sessions.max-per-user:3}")
    private int maxSessions = 3;

    @Value("${identity.sessions.absolute-lifetime:P30D}")
    private Duration absoluteLifetime = Duration.ofDays(30);

    @Value("${identity.admin-session.ttl:PT8H}")
    private Duration adminLifetime = Duration.ofHours(8);

    public List<SessionInfo> list(UUID userId) {
        if (userId == null) throw unauthorized();
        String json = read("USER_SESSIONS:" + userId);
        if (json == null) return new ArrayList<>();
        try {
            if (json.length() > MAX_REGISTRY_BYTES
                    || json.getBytes(StandardCharsets.UTF_8).length > MAX_REGISTRY_BYTES)
                throw new IllegalStateException();
            JsonNode tree = mapper.readerFor(JsonNode.class)
                    .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(json);
            if (!tree.isArray()) throw new IllegalStateException();
            // Check authority scalars before DTO binding: Jackson otherwise coerces a numeric
            // sessionId to text and an enum ordinal to LOGIN/ADMIN. Inspect every row, even an
            // expired one, before filtering; corruption cannot hide behind a valid first match.
            for (JsonNode row : tree) {
                if (!row.isObject() || !row.path("sessionId").isTextual()
                        || !row.path("purpose").isTextual()
                        || !("LOGIN".equals(row.path("purpose").textValue())
                                || "ADMIN".equals(row.path("purpose").textValue()))
                        || (row.has("lastActive") && (!row.path("lastActive").isIntegralNumber()
                                || !row.path("lastActive").canConvertToLong()
                                || row.path("lastActive").longValue() < 0)))
                    throw new IllegalStateException();
            }
            List<SessionInfo> rows =
                    mapper.readerFor(new TypeReference<List<SessionInfo>>() {})
                            .with(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                            .readValue(tree);
            Set<String> ids = new HashSet<>();
            for (SessionInfo row : rows) {
                if (row.getSessionId().isBlank() || !ids.add(row.getSessionId())
                        || row.getPurpose() == null || row.getCreatedAt() == null
                        || row.getAbsoluteExpiresAt() == null
                        || !row.getAbsoluteExpiresAt().isAfter(row.getCreatedAt()))
                    throw new IllegalStateException();
            }
            Instant now = clock.instant();
            return rows.stream()
                    .filter(s -> s.getAbsoluteExpiresAt().isAfter(now))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        } catch (Exception ex) {
            throw unavailable();
        }
    }

    public SessionInfo require(UUID userId, String sid) {
        if (userId == null || sid == null || sid.isBlank()
                || read("BLACKLIST:SESSION:" + sid) != null) throw unauthorized();
        var session = list(userId).stream()
                .filter(s -> sid.equals(s.getSessionId()))
                .findFirst()
                .orElseThrow(SessionRegistry::unauthorized);
        // A dependency wait may cross the expiry boundary after the list's snapshot.
        if (!session.getAbsoluteExpiresAt().isAfter(clock.instant())) throw unauthorized();
        return session;
    }

    private String read(String key) {
        try {
            return cache.get(key);
        } catch (RuntimeException ex) {
            throw unavailable();
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Session information is temporarily unavailable");
    }

    /** Builds a candidate without consuming the OTP or changing Redis on a 409. */
    public List<SessionInfo> prepare(
            UUID userId,
            OtpPurpose purpose,
            String device,
            String os,
            String browser,
            String replacement,
            boolean sameDeviceRecovery) {
        var rows = list(userId);
        if (replacement != null && !replacement.isBlank()) {
            if (!rows.removeIf(s -> replacement.equals(s.getSessionId())))
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Choose one of your active sessions to replace");
        } else if (sameDeviceRecovery && rows.size() >= maxSessions && os != null && browser != null) {
            // Same-device recovery replaces only a session with the same authentication purpose.
            rows.stream()
                    .filter(
                            s ->
                                    s.getPurpose() == purpose
                                            && os.equals(s.getOs())
                                            && browser.equals(s.getBrowser()))
                    .min(Comparator.comparingLong(SessionInfo::getLastActive))
                    .ifPresent(rows::remove);
        }
        if (rows.size() >= maxSessions)
            throw new MaxSessionsReachedException("Maximum concurrent sessions reached", rows);
        var now = clock.instant();
        rows.add(
                SessionInfo.builder()
                        .sessionId(UUID.randomUUID().toString())
                        .purpose(purpose)
                        .deviceInfo(text(device, "Unknown Device"))
                        .os(text(os, "Unknown OS"))
                        .browser(text(browser, "Unknown Browser"))
                        .createdAt(now)
                        .absoluteExpiresAt(
                                now.plus(
                                        switch (purpose) {
                                            case LOGIN -> absoluteLifetime;
                                            case ADMIN -> adminLifetime;
                                        }))
                        .lastActive(now.toEpochMilli())
                        .build());
        return rows;
    }

    public void commitNew(UUID userId, List<SessionInfo> candidate) {
        var retained =
                candidate.stream()
                        .map(SessionInfo::getSessionId)
                        .collect(java.util.stream.Collectors.toSet());
        for (var previous : list(userId))
            if (!retained.contains(previous.getSessionId())) blacklist(previous);
        save(userId, candidate);
    }

    public void touch(UUID userId, String sid) {
        var rows = list(userId);
        rows.stream()
                .filter(s -> sid.equals(s.getSessionId()))
                .forEach(s -> s.setLastActive(clock.millis()));
        save(userId, rows);
    }

    public void remove(UUID userId, String sid, boolean requireOwned) {
        var rows = list(userId);
        var target = rows.stream().filter(s -> sid.equals(s.getSessionId())).findFirst();
        if (target.isEmpty()) {
            if (requireOwned)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
            return;
        }
        blacklist(target.get());
        rows.remove(target.get());
        save(userId, rows);
    }

    public void removeAll(UUID userId) {
        list(userId).forEach(this::blacklist);
        cache.delete("USER_SESSIONS:" + userId);
    }

    private void blacklist(SessionInfo session) {
        cache.put(
                "BLACKLIST:SESSION:" + session.getSessionId(),
                "true",
                minutesUntil(session.getAbsoluteExpiresAt()));
    }

    private void save(UUID userId, List<SessionInfo> rows) {
        if (rows.isEmpty()) {
            cache.delete("USER_SESSIONS:" + userId);
            return;
        }
        String json;
        try {
            json = mapper.writeValueAsString(rows);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot serialize session information");
        }
        var last =
                rows.stream()
                        .map(SessionInfo::getAbsoluteExpiresAt)
                        .max(Comparator.naturalOrder())
                        .orElseThrow();
        cache.put("USER_SESSIONS:" + userId, json, minutesUntil(last));
    }

    private long minutesUntil(Instant expiry) {
        return Math.max(
                1,
                (Math.max(0, Duration.between(clock.instant(), expiry).toMillis()) + 59_999)
                        / 60_000);
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Session expired; sign in again");
    }
}
