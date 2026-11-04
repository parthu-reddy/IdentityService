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
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.nio.charset.StandardCharsets;
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

import static com.fooddelivery.identity.exception.MapsIdentityException.Code.INACTIVE;

/** A scoped token profile over existing accounts/person sessions, never a second identity authority. */
public final class MapsSessionTokens {
    public static final String TYPE = "maps-session+jwt";
    private static final ObjectReader JSON = new ObjectMapper().readerFor(JsonNode.class)
            .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> HEADER = Set.of("alg", "typ", "kid");
    private static final Set<String> CLAIMS = Set.of("iss", "aud", "sub", "session_id", "ev", "iat", "exp");
    private final MapsIdentityProperties properties;
    private final Clock clock;
    private final PrivateKey signingKey;
    private final Map<String, PublicKey> keys;

    public record Verified(UUID subject, String sessionId, long version, Instant issuedAt, Instant expiresAt) {}

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
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiry = now.plus(properties.getCredentialLifetime());
        if (session.getAbsoluteExpiresAt().isBefore(expiry)) expiry = session.getAbsoluteExpiresAt().truncatedTo(ChronoUnit.SECONDS);
        if (!user.isActive() || user.getEntitlementsVersion() < 0 || session.getPurpose() != OtpPurpose.LOGIN
                || !expiry.isAfter(now)) throw new MapsIdentityException(INACTIVE);
        return Jwts.builder().setHeaderParam("typ", TYPE).setHeaderParam("kid", properties.getSigningKeyId())
                .setIssuer(properties.getAuthority()).setAudience(properties.getAudience())
                .setSubject(user.getId().toString()).claim("session_id", session.getSessionId())
                .claim("ev", user.getEntitlementsVersion()).setIssuedAt(Date.from(now)).setExpiration(Date.from(expiry))
                .signWith(signingKey, SignatureAlgorithm.RS256).compact();
    }

    /** allowExpired is ONLY for session-specific revocation, never validation/refresh. */
    public Verified verify(String token, boolean allowExpired) {
        try {
            if (token == null || token.length() > 4096) throw new IllegalArgumentException();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) throw new IllegalArgumentException();
            for (String part : parts) if (!part.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException();
            JsonNode header = JSON.readValue(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode body = JSON.readValue(Base64.getUrlDecoder().decode(parts[1]));
            if (!exactKeys(header, HEADER) || !exactKeys(body, CLAIMS)
                    || !"RS256".equals(text(header, "alg")) || !TYPE.equals(text(header, "typ"))) throw new IllegalArgumentException();
            PublicKey key = keys.get(text(header, "kid"));
            if (key == null) throw new IllegalArgumentException();
            var parser = Jwts.parserBuilder().setSigningKey(key).requireIssuer(properties.getAuthority())
                    .requireAudience(properties.getAudience()).setClock(() -> Date.from(clock.instant())).build();
            try {
                parser.parseClaimsJws(token);
            } catch (ExpiredJwtException expired) {
                // JJWT verifies the signature before throwing expiration. Strict scope/claim checks
                // below still run, including the upper lifetime and future-issued constraints.
                if (!allowExpired) throw expired;
            }
            if (!properties.getAuthority().equals(text(body, "iss")) || !properties.getAudience().equals(text(body, "aud")))
                throw new IllegalArgumentException();
            String subject = text(body, "sub"), sid = text(body, "session_id");
            UUID id = canonicalUuid(subject);
            canonicalUuid(sid);
            long version = number(body, "ev"), issued = number(body, "iat"), expires = number(body, "exp");
            Instant iat = Instant.ofEpochSecond(issued), exp = Instant.ofEpochSecond(expires), now = clock.instant();
            if (version < 0 || !exp.isAfter(iat) || exp.isAfter(iat.plus(properties.getCredentialLifetime()))
                    || iat.isAfter(now) || (!allowExpired && !exp.isAfter(now))) throw new IllegalArgumentException();
            return new Verified(id, sid, version, iat, exp);
        } catch (Exception ex) {
            throw new MapsIdentityException(INACTIVE);
        }
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
