package com.fooddelivery.identity.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.identity.dto.SessionInfo;
import com.fooddelivery.identity.exception.MaxSessionsReachedException;
import com.fooddelivery.identity.port.CachePort;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

/** Call mutations while holding the person's DB lock; no process-local synchronization. */
@Service
@lombok.RequiredArgsConstructor
public class SessionRegistry {
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
        String json = cache.get("USER_SESSIONS:" + userId);
        if (json == null) return new ArrayList<>();
        try {
            List<SessionInfo> rows =
                    mapper.readerFor(new TypeReference<List<SessionInfo>>() {})
                            .with(
                                    com.fasterxml.jackson.databind.DeserializationFeature
                                            .FAIL_ON_TRAILING_TOKENS)
                            .readValue(json);
            if (rows == null
                    || rows.stream()
                            .anyMatch(
                                    s ->
                                            s.getSessionId() == null
                                                    || s.getPurpose() == null
                                                    || s.getCreatedAt() == null
                                                    || s.getAbsoluteExpiresAt() == null))
                throw new IllegalStateException();
            return rows.stream()
                    .filter(s -> s.getAbsoluteExpiresAt().isAfter(clock.instant()))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Session information is temporarily unavailable");
        }
    }

    public SessionInfo require(UUID userId, String sid) {
        if (sid == null || cache.get("BLACKLIST:SESSION:" + sid) != null) throw unauthorized();
        return list(userId).stream()
                .filter(s -> sid.equals(s.getSessionId()))
                .findFirst()
                .orElseThrow(SessionRegistry::unauthorized);
    }

    /** Builds a candidate without consuming the OTP or changing Redis on a 409. */
    public List<SessionInfo> prepare(
            UUID userId,
            OtpPurpose purpose,
            String device,
            String os,
            String browser,
            String replacement) {
        var rows = list(userId);
        if (replacement != null && !replacement.isBlank()) {
            if (!rows.removeIf(s -> replacement.equals(s.getSessionId())))
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Choose one of your active sessions to replace");
        } else if (rows.size() >= maxSessions && os != null && browser != null) {
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
                                        purpose == OtpPurpose.ADMIN
                                                ? adminLifetime
                                                : absoluteLifetime))
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
