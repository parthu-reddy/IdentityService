package com.fooddelivery.identity.service;

import com.fooddelivery.common.audit.AuditTrail;
import com.fooddelivery.common.constants.RedisKeyConstants;
import com.fooddelivery.common.enums.*;
import com.fooddelivery.identity.dto.AuthRequests.*;
import com.fooddelivery.identity.dto.SessionInfo;
import com.fooddelivery.identity.entitlement.service.EntitlementService;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.port.*;
import com.fooddelivery.identity.repository.UserRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.micrometer.core.instrument.MeterRegistry;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.time.*;
import java.util.*;

@Service
@lombok.RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final UserProvisioner provisioner;
    private final EventPublisherPort events;
    private final CachePort cache;
    private final SessionRegistry sessions;
    private final EntitlementService entitlements;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final AuditTrail audit;
    private final SecureRandom random = new SecureRandom();

    @Value("${jwt.private-key.path:classpath:certs/private.pem}")
    private Resource privateKeyResource;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationMs = 86400000;

    @Value("${identity.admin-session.ttl:PT8H}")
    private Duration adminLifetime = Duration.ofHours(8);

    @Value("${identity.auth.initiate.max-attempts:10}")
    private int initiateMaxAttempts = 10;

    @Value("${identity.auth.initiate.window-minutes:10}")
    private int initiateWindowMinutes = 10;

    @Value("${identity.auth.verify.max-attempts:5}")
    private int verifyMaxAttempts = 5;

    @Value("${identity.auth.verify.window-minutes:5}")
    private int verifyWindowMinutes = 5;

    private PrivateKey privateKey;

    @PostConstruct
    public void init() {
        try {
            var pem =
                    new String(
                            FileCopyUtils.copyToByteArray(privateKeyResource.getInputStream()),
                            StandardCharsets.UTF_8);
            var encoded =
                    pem.replace("-----BEGIN PRIVATE KEY-----", "")
                            .replace("-----END PRIVATE KEY-----", "")
                            .replaceAll("\\s", "");
            privateKey =
                    KeyFactory.getInstance("RSA")
                            .generatePrivate(
                                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)));
            if (jwtExpirationMs <= 0 || adminLifetime.isNegative() || adminLifetime.isZero())
                throw new IllegalArgumentException();
        } catch (Exception ex) {
            throw new IllegalStateException("Authentication signing configuration is unavailable");
        }
    }

    public void initiateLogin(String phone) {
        sendOtp(validPhone(phone), OtpPurpose.LOGIN);
    }

    @Transactional
    public SessionResponse createSession(SessionRequest request) {
        String phone = validPhone(request.phoneNumber());
        verifyOtp(phone, request.otp(), OtpPurpose.LOGIN);
        var provisioned = provisioner.findOrCreate(phone);
        var user = provisioned.user();
        var candidate =
                sessions.prepare(
                        user.getId(),
                        OtpPurpose.LOGIN,
                        request.deviceInfo(),
                        request.os(),
                        request.browser(),
                        request.replaceSessionId());
        var roles = entitlements.rolesFor(user.getId());
        consumeOtp(phone, request.otp(), OtpPurpose.LOGIN);
        sessions.commitNew(user.getId(), candidate);
        cache.delete("RATELIMIT:VERIFY:" + phone);
        return new SessionResponse(
                token(user, roles, candidate.get(candidate.size() - 1)), provisioned.created());
    }

    @Transactional
    public SessionResponse refresh(UUID userId, String sid) {
        try {
            var user = provisioner.lockActive(userId);
            var session = sessions.require(userId, sid);
            limit(RedisKeyConstants.RL_SESSION_REFRESH + sid, 60, 30);
            var roles =
                    session.getPurpose() == OtpPurpose.ADMIN
                            ? adminRoles(userId)
                            : entitlements.rolesFor(userId);
            String token = token(user, roles, session);
            sessions.touch(userId, sid);
            metrics.counter("auth.session.refresh", "reason", "requested", "result", "success")
                    .increment();
            return new SessionResponse(token, false);
        } catch (RuntimeException ex) {
            metrics.counter("auth.session.refresh", "reason", "requested", "result", "refused")
                    .increment();
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public void initiateAdminOtp(UUID userId, String sid) {
        var user = entitlements.requireActiveUser(userId);
        adminRoles(userId);
        sessions.require(userId, sid);
        sendOtp(user.getPhoneNumber(), OtpPurpose.ADMIN);
    }

    @Transactional
    public SessionResponse createAdminSession(
            UUID userId, String sourceSid, AdminSessionRequest request) {
        try {
            var user = provisioner.lockActive(userId);
            var source = sessions.require(userId, sourceSid);
            var roles = adminRoles(userId);
            verifyOtp(user.getPhoneNumber(), request.otp(), OtpPurpose.ADMIN);
            if (sourceSid.equals(request.replaceSessionId()))
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Choose another session so your everyday sign-in remains available");
            var candidate =
                    sessions.prepare(
                            userId,
                            OtpPurpose.ADMIN,
                            source.getDeviceInfo(),
                            source.getOs(),
                            source.getBrowser(),
                            request.replaceSessionId());
            audit.record(
                    clock.instant(),
                    userId,
                    "ADMIN",
                    AuditAction.ADMIN_STEP_UP,
                    "USER",
                    userId,
                    null,
                    null,
                    Map.of());
            consumeOtp(user.getPhoneNumber(), request.otp(), OtpPurpose.ADMIN);
            sessions.commitNew(userId, candidate);
            String token = token(user, roles, candidate.get(candidate.size() - 1));
            metrics.counter("auth.admin_stepup", "result", "issued").increment();
            return new SessionResponse(token, false);
        } catch (RuntimeException ex) {
            metrics.counter("auth.admin_stepup", "result", "refused").increment();
            throw ex;
        }
    }

    @Transactional
    public void logout(UUID userId, String sid) {
        users.lockById(userId);
        sessions.remove(userId, sid, false);
    }

    @Transactional(readOnly = true)
    public List<SessionInfo> getUserSessions(UUID userId) {
        entitlements.requireActiveUser(userId);
        return sessions.list(userId);
    }

    @Transactional
    public void removeSession(UUID userId, String sid) {
        provisioner.lockActive(userId);
        sessions.remove(userId, sid, true);
    }

    // Administrative account changes invoke this after their own DB commit.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeAllSessions(UUID userId) {
        users.lockById(userId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Account not found"));
        sessions.removeAll(userId);
    }

    private List<RoleName> adminRoles(UUID userId) {
        if (!entitlements.isAdministrator(userId))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Administrator access is required");
        return List.of(RoleName.ADMIN);
    }

    /** Initiate and verify limits per sign-in purpose; exhaustive, so a new purpose cannot inherit LOGIN's. */
    private record OtpLimits(
            String initiateKey,
            long initiateMinutes,
            int initiateMax,
            String verifyKey,
            long verifyMinutes,
            int verifyMax) {}

    private OtpLimits limits(String phone, OtpPurpose purpose) {
        return switch (purpose) {
            case LOGIN ->
                    new OtpLimits(
                            "RATELIMIT:INITIATE:" + phone,
                            initiateWindowMinutes,
                            initiateMaxAttempts,
                            "RATELIMIT:VERIFY:" + phone,
                            verifyWindowMinutes,
                            verifyMaxAttempts);
            case ADMIN ->
                    new OtpLimits(
                            RedisKeyConstants.RL_ADMIN_OTP_INITIATE + phone,
                            10,
                            10,
                            RedisKeyConstants.RL_ADMIN_OTP_VERIFY + phone,
                            5,
                            5);
            case DINE_IN ->
                    throw new IllegalArgumentException(
                            "Dine-in codes are issued by DineInConsentService and never sign in");
        };
    }

    private void sendOtp(String phone, OtpPurpose purpose) {
        var limits = limits(phone, purpose);
        limit(limits.initiateKey(), limits.initiateMinutes(), limits.initiateMax());
        String otp = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        cache.put(otpKey(phone, purpose), otp, 5);
        events.publishNotificationEvent(phone, "SMS", otp);
    }

    private void verifyOtp(String phone, String otp, OtpPurpose purpose) {
        var limits = limits(phone, purpose);
        try {
            limit(limits.verifyKey(), limits.verifyMinutes(), limits.verifyMax());
        } catch (ResponseStatusException ex) {
            cache.delete(otpKey(phone, purpose));
            throw ex;
        }
        String expected = cache.get(otpKey(phone, purpose));
        if (otp == null
                || !otp.matches("[0-9]{6}")
                || expected == null
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        otp.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired code");
    }

    private void consumeOtp(String phone, String otp, OtpPurpose purpose) {
        if (!cache.consumeOtp(otpKey(phone, purpose), otp))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired code");
    }

    private void limit(String key, long minutes, int maximum) {
        Long attempts = cache.increment(key, minutes);
        if (attempts == null)
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Authentication is temporarily unavailable");
        if (attempts > maximum)
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Too many attempts; try again later");
    }

    private String token(AppUser user, List<RoleName> roles, SessionInfo session) {
        var now = clock.instant();
        if (!session.getAbsoluteExpiresAt().isAfter(now))
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Session expired; sign in again");
        var expiry =
                now.plusMillis(
                        session.getPurpose() == OtpPurpose.ADMIN
                                ? adminLifetime.toMillis()
                                : jwtExpirationMs);
        if (expiry.isAfter(session.getAbsoluteExpiresAt())) expiry = session.getAbsoluteExpiresAt();
        cache.putVersions(
                Map.of(
                        RedisKeyConstants.ENTITLEMENTS_VERSION + user.getId(),
                        Long.toString(user.getEntitlementsVersion())),
                Duration.ofMillis(
                        Math.multiplyExact(
                                2, Math.max(jwtExpirationMs, adminLifetime.toMillis()))));
        return Jwts.builder()
                .setSubject(user.getId().toString())
                .claim("phone", user.getPhoneNumber())
                .claim("roles", roles.stream().map(Enum::name).toList())
                .claim("sessionId", session.getSessionId())
                .claim("ev", user.getEntitlementsVersion())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    private static String validPhone(String phone) {
        if (phone == null || !phone.matches("[0-9]{10}"))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Enter a valid 10-digit phone number");
        return phone;
    }

    private static String otpKey(String phone, OtpPurpose purpose) {
        return "OTP:" + phone + ":" + purpose.name();
    }
}
