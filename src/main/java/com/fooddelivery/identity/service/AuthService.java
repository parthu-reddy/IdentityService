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

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<MapsSessionTokens> mapsSessionTokens;

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

    @Value("${identity.maps.otp-max-per-day:1000}")
    private int mapsOtpMaxPerDay = 1000;

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

    public void initiateMapsLogin(String phone) {
        phone = validPhone(phone);
        if (mapsOtpMaxPerDay < 1 || mapsOtpMaxPerDay > 10000)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        // Shared atomic Redis admission bounds notification spending across peers,
        // replicas and phone rotations. It is charged only for a notification the
        // per-phone limit admits: a refused request sends nothing, so repeating one
        // phone must not exhaust the budget for every other Maps sign-in.
        var limits = limits(phone, OtpPurpose.LOGIN);
        mapsOtpLimit(limits.initiateKey(), limits.initiateMinutes(), limits.initiateMax());
        mapsOtpLimit("RATELIMIT:MAPS:OTP:GLOBAL", 1440, mapsOtpMaxPerDay);
        deliverOtp(phone, OtpPurpose.LOGIN);
    }

    @Transactional
    public SessionResponse createSession(SessionRequest request) {
        return createPersonSession(request, null);
    }

    /** Dedicated peer-authenticated Maps entry; the ordinary credential profile is unchanged. */
    @Transactional
    public SessionResponse createMapsSession(SessionRequest request) {
        return createPersonSession(request, requireMapsTokens());
    }

    private SessionResponse createPersonSession(SessionRequest request, MapsSessionTokens mapsTokens) {
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
                        request.replaceSessionId(),
                        // Recovery assumes a repeat login on this device. A Maps sign-in on the same
                        // browser is a different product, so it must not end a Food Delivery session
                        // unasked: the person picks one from the session-limit response instead.
                        mapsTokens == null);
        var roles = mapsTokens == null ? entitlements.rolesFor(user.getId()) : List.<RoleName>of();
        if (mapsTokens != null) {
            // The Maps relay is absolute-lifetime bounded. An unclaimed/lost login must not
            // occupy the shared person's session allowance for the ordinary 30-day lifetime.
            var created = candidate.get(candidate.size() - 1);
            var limit = clock.instant().plus(Duration.ofMinutes(15));
            if (limit.isBefore(created.getAbsoluteExpiresAt())) created.setAbsoluteExpiresAt(limit);
        }
        consumeOtp(phone, request.otp(), OtpPurpose.LOGIN);
        sessions.commitNew(user.getId(), candidate);
        cache.delete("RATELIMIT:VERIFY:" + phone);
        return new SessionResponse(
                mapsTokens == null ? token(user, roles, candidate.get(candidate.size() - 1))
                        : mapsTokens.issue(user, candidate.get(candidate.size() - 1)), provisioned.created());
    }

    @Transactional
    public SessionResponse refreshMaps(String credential) {
        var tokens = requireMapsTokens();
        var verified = tokens.verify(credential, false);
        var user = provisioner.lockActive(verified.subject());
        var session = sessions.require(user.getId(), verified.sessionId());
        if (user.getEntitlementsVersion() != verified.version() || session.getPurpose() != OtpPurpose.LOGIN
                || verified.expiresAt().isAfter(session.getAbsoluteExpiresAt())
                || verified.issuedAt().isBefore(session.getCreatedAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)))
            throw new com.fooddelivery.identity.exception.MapsIdentityException(
                    com.fooddelivery.identity.exception.MapsIdentityException.Code.INACTIVE);
        limit(RedisKeyConstants.RL_SESSION_REFRESH + session.getSessionId(), 60, 30);
        String credentialResult = tokens.issue(user, session);
        sessions.touch(user.getId(), session.getSessionId());
        return new SessionResponse(credentialResult, false);
    }

    @Transactional
    public void logoutMaps(String credential) {
        var verified = requireMapsTokens().verify(credential, true);
        // Same DB row lock as all person-session refresh/revoke transitions. Do not require an
        // active account/version before revoking: disabled/expired sessions must still sign out.
        users.lockById(verified.subject());
        sessions.remove(verified.subject(), verified.sessionId(), false);
    }

    private MapsSessionTokens requireMapsTokens() {
        var tokens = mapsSessionTokens == null ? null : mapsSessionTokens.getIfAvailable();
        if (tokens == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Authentication is temporarily unavailable");
        return tokens;
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
                            request.replaceSessionId(),
                            true);
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
        };
    }

    private void sendOtp(String phone, OtpPurpose purpose) {
        var limits = limits(phone, purpose);
        limit(limits.initiateKey(), limits.initiateMinutes(), limits.initiateMax());
        deliverOtp(phone, purpose);
    }

    private void deliverOtp(String phone, OtpPurpose purpose) {
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

    private void mapsOtpLimit(String key, long minutes, int maximum) {
        try {
            limit(key, minutes, maximum);
        } catch (ResponseStatusException refused) {
            if (refused.getStatusCode().value() != 429) throw refused;
            // CachePort has no TTL-read operation. Advertise the full configured
            // window conservatively; do not pretend this is its remaining TTL.
            throw new com.fooddelivery.identity.exception.MapsOtpRateLimitException(
                    Math.max(1, Math.min(86400, minutes * 60)));
        }
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
