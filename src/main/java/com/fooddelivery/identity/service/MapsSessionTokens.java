package com.fooddelivery.identity.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fooddelivery.identity.config.MapsIdentityProperties;
import com.fooddelivery.identity.dto.SessionInfo;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.exception.MapsIdentityException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static com.fooddelivery.identity.exception.MapsIdentityException.Code.INACTIVE;

/** A scoped token profile over existing accounts/person sessions, never a second identity authority. */
public final class MapsSessionTokens {
    public static final String TYPE = "maps-session+jwt";
    /** MDC key the Maps filter sets for its worker thread; only the opaque request correlation. */
    public static final String CORRELATION = "maps_correlation";
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MapsSessionTokens.class);
    private static final ObjectReader JSON = new ObjectMapper().readerFor(JsonNode.class)
            .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> HEADER = Set.of("alg", "typ", "kid");
    private static final Set<String> CLAIMS = Set.of("iss", "aud", "sub", "session_id", "ev", "iat", "exp");
    private static final Set<String> PUBLISHER_CLAIMS =
            Set.of("iss", "aud", "sub", "session_id", "ev", "iat", "exp", "mfa_at");
    static final java.time.Duration PUBLISHER_FRESHNESS = java.time.Duration.ofMinutes(5);
    private final MapsIdentityProperties properties;
    private final Clock clock;
    private final PrivateKey signingKey;
    private final Map<String, PublicKey> keys;

    private final String publisherPasswordHash;

    public record Verified(UUID subject, String sessionId, long version, Instant issuedAt, Instant expiresAt, Instant mfaAt) {
        public Verified(UUID subject, String sessionId, long version, Instant issuedAt, Instant expiresAt) {
            this(subject, sessionId, version, issuedAt, expiresAt, null);
        }
    }

    public MapsSessionTokens(MapsIdentityProperties properties, Clock clock, ResourceLoader resources) {
        this.properties = properties;
        this.clock = clock;
        properties.validate();
        try {
            var file = java.nio.file.Path.of(java.net.URI.create(properties.getPrivateKey()));
            var permissions = java.nio.file.Files.getPosixFilePermissions(file);
            if (permissions.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_READ)
                    || permissions.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE)
                    || permissions.contains(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE))
                throw new IllegalArgumentException();
            var factory = KeyFactory.getInstance("RSA");
            signingKey = factory.generatePrivate(new PKCS8EncodedKeySpec(pem(resources, properties.getPrivateKey(), "PRIVATE KEY")));
            var loaded = new HashMap<String, PublicKey>();
            for (var entry : properties.effectiveVerificationKeys().entrySet()) {
                var key = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(pem(resources, entry.getValue(), "PUBLIC KEY")));
                if (key.getModulus().bitLength() < 2048) throw new IllegalArgumentException();
                loaded.put(entry.getKey(), key);
            }
            var privateRsa = (RSAPrivateCrtKey) signingKey;
            var publicRsa = (RSAPublicKey) loaded.get(properties.getSigningKeyId());
            if (!privateRsa.getModulus().equals(publicRsa.getModulus())
                    || !privateRsa.getPublicExponent().equals(publicRsa.getPublicExponent())) throw new IllegalArgumentException();
            // The repository's published example key must never become Maps production authority.
            var development = factory.generatePublic(new X509EncodedKeySpec(pem(resources, "classpath:certs/public.pem", "PUBLIC KEY")));
            if (loaded.values().stream().anyMatch(k -> java.util.Arrays.equals(development.getEncoded(), k.getEncoded())))
                throw new IllegalArgumentException();
            keys = Map.copyOf(loaded);
        } catch (Exception ex) {
            throw new IllegalStateException("Maps signing configuration is unavailable");
        }
        publisherPasswordHash = loadPublisherPassword(properties.getPublisherPasswordFile());
    }

    private static String loadPublisherPassword(String file) {
        if (file == null) return null;
        try {
            Path path = Path.of(file);
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException();
            var permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
            if (permissions.contains(PosixFilePermission.OTHERS_READ)
                    || permissions.contains(PosixFilePermission.OTHERS_WRITE)
                    || permissions.contains(PosixFilePermission.GROUP_WRITE)) throw new IllegalArgumentException();
            try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                String hash = new String(input.readNBytes(63), StandardCharsets.US_ASCII).stripTrailing();
                if (!hash.matches("\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}")) throw new IllegalArgumentException();
                return hash;
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Maps publisher password configuration is unavailable");
        }
    }

    Instant authenticatePublisher(UUID subject, String password) {
        if (publisherPasswordHash == null || !subject.equals(properties.getPublisherSubject())
                || !password.matches("[!-~]{16,72}")
                || !new BCryptPasswordEncoder().matches(password, publisherPasswordHash))
            throw new MapsIdentityException(INACTIVE);
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    private static byte[] pem(ResourceLoader resources, String path, String label) throws Exception {
        try (var input = resources.getResource(path).getInputStream()) {
            byte[] bytes = input.readNBytes(16_385);
            if (bytes.length > 16_384) throw new IllegalArgumentException();
            String text = new String(bytes, StandardCharsets.US_ASCII);
            return Base64.getDecoder().decode(text.replace("-----BEGIN " + label + "-----", "")
                    .replace("-----END " + label + "-----", "").replaceAll("\\s", ""));
        }
    }

    public String issue(AppUser user, SessionInfo session) {
        return issue(user, session, null);
    }

    String issue(AppUser user, SessionInfo session, Instant mfaAt) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiry = now.plus(properties.getCredentialLifetime());
        if (session.getAbsoluteExpiresAt().isBefore(expiry)) expiry = session.getAbsoluteExpiresAt().truncatedTo(ChronoUnit.SECONDS);
        if (!user.isActive() || user.getEntitlementsVersion() < 0 || session.getPurpose() != OtpPurpose.LOGIN
                || !expiry.isAfter(now)) throw new MapsIdentityException(INACTIVE);
        var builder = Jwts.builder().setHeaderParam("typ", TYPE).setHeaderParam("kid", properties.getSigningKeyId())
                .setIssuer(properties.getAuthority()).setAudience(properties.getAudience())
                .setSubject(user.getId().toString()).claim("session_id", session.getSessionId())
                .claim("ev", user.getEntitlementsVersion()).setIssuedAt(Date.from(now)).setExpiration(Date.from(expiry));
        if (mfaAt != null && publisherPasswordHash != null && user.getId().equals(properties.getPublisherSubject())
                && !mfaAt.isAfter(now) && now.isBefore(mfaAt.plus(PUBLISHER_FRESHNESS)))
            builder.claim("mfa_at", mfaAt.getEpochSecond());
        return builder.signWith(signingKey, SignatureAlgorithm.RS256).compact();
    }

    /**
     * The first verifier check a refused credential failed. Logged by name only (never claim values or
     * token bytes) so an operator can tell which boundary refused it; the caller still sees one INACTIVE.
     */
    public enum Check { FORMAT, HEADER, CLAIM_SET, ALGORITHM, TYPE, KEY_ID, SIGNATURE, ISSUER, AUDIENCE,
        EXPIRED, PREMATURE, LIFETIME, SUBJECT }

    private static final class Rejected extends RuntimeException {
        final Check check;
        Rejected(Check check) { super(check.name(), null, false, false); this.check = check; }
    }
    private static void require(boolean condition, Check check) { if (!condition) throw new Rejected(check); }

    /** allowExpired is ONLY for session-specific revocation, never validation/refresh. */
    public Verified verify(String token, boolean allowExpired) {
        try {
            return verified(token, allowExpired);
        } catch (Rejected rejected) {
            throw refuse(rejected.check);
        } catch (Exception ex) {
            throw refuse(Check.FORMAT);
        }
    }

    private static MapsIdentityException refuse(Check check) {
        LOG.info("maps_credential operation=verify stage={} cause=rejected correlation={}", check.name().toLowerCase(java.util.Locale.ROOT),
                java.util.Objects.requireNonNullElse(org.slf4j.MDC.get(CORRELATION), "none"));
        return new MapsIdentityException(INACTIVE);
    }

    private Verified verified(String token, boolean allowExpired) throws Exception {
        require(token != null && token.length() <= 4096, Check.FORMAT);
        String[] parts = token.split("\\.", -1);
        require(parts.length == 3, Check.FORMAT);
        for (String part : parts) require(part.matches("[A-Za-z0-9_-]+"), Check.FORMAT);
        JsonNode header = JSON.readValue(Base64.getUrlDecoder().decode(parts[0]));
        JsonNode body = JSON.readValue(Base64.getUrlDecoder().decode(parts[1]));
        // Only the three expected header fields: a token can never name its own key source (jku, x5u, jwk, x5c).
        require(exactKeys(header, HEADER), Check.HEADER);
        boolean publisher = exactKeys(body, PUBLISHER_CLAIMS);
        require(publisher || exactKeys(body, CLAIMS), Check.CLAIM_SET);
        require("RS256".equals(text(header, "alg")), Check.ALGORITHM);
        require(TYPE.equals(text(header, "typ")), Check.TYPE);
        PublicKey key = keys.get(text(header, "kid"));
        require(key != null, Check.KEY_ID);
        var parser = Jwts.parserBuilder().setSigningKey(key).requireIssuer(properties.getAuthority())
                .requireAudience(properties.getAudience()).setClock(() -> Date.from(clock.instant())).build();
        try {
            parser.parseClaimsJws(token);
        } catch (ExpiredJwtException expired) {
            // JJWT verifies the signature before throwing expiration. Strict scope/claim checks
            // below still run, including the upper lifetime and future-issued constraints.
            if (!allowExpired) throw new Rejected(Check.EXPIRED);
        } catch (io.jsonwebtoken.IncorrectClaimException | io.jsonwebtoken.MissingClaimException claim) {
            throw new Rejected(Claims.ISSUER.equals(claim.getClaimName()) ? Check.ISSUER
                    : Claims.AUDIENCE.equals(claim.getClaimName()) ? Check.AUDIENCE : Check.CLAIM_SET);
        } catch (io.jsonwebtoken.security.SecurityException signature) {
            throw new Rejected(Check.SIGNATURE);
        }
        require(properties.getAuthority().equals(text(body, "iss")), Check.ISSUER);
        require(properties.getAudience().equals(text(body, "aud")), Check.AUDIENCE);
        String subject = text(body, "sub"), sid = text(body, "session_id");
        UUID id;
        try { id = canonicalUuid(subject); canonicalUuid(sid); }
        catch (IllegalArgumentException invalid) { throw new Rejected(Check.SUBJECT); }
        long version = number(body, "ev"), issued = number(body, "iat"), expires = number(body, "exp");
        Instant iat = Instant.ofEpochSecond(issued), exp = Instant.ofEpochSecond(expires), now = clock.instant();
        require(version >= 0, Check.CLAIM_SET);
        // Zero clock skew: iat is inclusive (iat == now is current) and exp is exclusive (exp == now is expired).
        require(!iat.isAfter(now), Check.PREMATURE);
        require(allowExpired || exp.isAfter(now), Check.EXPIRED);
        require(exp.isAfter(iat) && !exp.isAfter(iat.plus(properties.getCredentialLifetime())), Check.LIFETIME);
        Instant mfaAt = publisher ? Instant.ofEpochSecond(number(body, "mfa_at")) : null;
        require(mfaAt == null || !mfaAt.isBefore(Instant.EPOCH) && !mfaAt.isAfter(iat), Check.CLAIM_SET);
        return new Verified(id, sid, version, iat, exp, mfaAt);
    }

    private static boolean exactKeys(JsonNode node, Set<String> expected) {
        if (!node.isObject() || node.size() != expected.size()) return false;
        var fields = node.fieldNames();
        while (fields.hasNext()) if (!expected.contains(fields.next())) return false;
        return true;
    }
    private static String text(JsonNode node, String name) {
        if (!node.path(name).isTextual()) throw new IllegalArgumentException();
        return node.path(name).textValue();
    }
    private static long number(JsonNode node, String name) {
        JsonNode field = node.path(name);
        if (!field.isIntegralNumber() || !field.canConvertToLong()) throw new IllegalArgumentException();
        return field.longValue();
    }
    private static UUID canonicalUuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) throw new IllegalArgumentException();
        return id;
    }
}
