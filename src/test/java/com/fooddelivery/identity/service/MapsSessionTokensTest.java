package com.fooddelivery.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.identity.config.MapsIdentityProperties;
import com.fooddelivery.identity.dto.SessionInfo;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.exception.MapsIdentityException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MapsSessionTokensTest {
    @TempDir Path temp;
    static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
    final ObjectMapper json = new ObjectMapper();
    final Clock clock = mock(Clock.class);
    MapsIdentityProperties properties;
    MapsSessionTokens tokens;
    KeyPair pair;
    AppUser user;
    SessionInfo session;

    @BeforeEach void setup() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        properties = configured(temp, pair);
        tokens = new MapsSessionTokens(properties, clock, new DefaultResourceLoader());
        user = AppUser.builder().id(UUID.randomUUID()).isActive(true).entitlementsVersion(4).build();
        session = SessionInfo.builder().sessionId(UUID.randomUUID().toString()).purpose(OtpPurpose.LOGIN)
                .createdAt(NOW.minusSeconds(30)).absoluteExpiresAt(NOW.plusSeconds(3600)).build();
    }

    static MapsIdentityProperties configured(Path temp, KeyPair pair) throws Exception {
        var properties = new MapsIdentityProperties();
        properties.setEnabled(true); properties.setAuthority("https://identity.test"); properties.setAudience("maps-editor");
        properties.setSigningKeyId("current"); properties.setAllowedPeers(List.of("spiffe://maps/adapter"));
        Path privatePath = temp.resolve("private.pem"), publicPath = temp.resolve("public.pem");
        Files.writeString(privatePath, pem(pair.getPrivate().getEncoded(), "PRIVATE KEY"));
        Files.setPosixFilePermissions(privatePath, PosixFilePermissions.fromString("rw-------"));
        Files.writeString(publicPath, pem(pair.getPublic().getEncoded(), "PUBLIC KEY"));
        properties.setPrivateKey(privatePath.toUri().toString());
        properties.setVerificationKeys(Map.of("current", publicPath.toUri().toString()));
        return properties;
    }
    static String pem(byte[] bytes, String type) {
        return "-----BEGIN " + type + "-----\n" + Base64.getEncoder().encodeToString(bytes) + "\n-----END " + type + "-----";
    }
    ObjectNode body(String credential) throws Exception {
        return (ObjectNode) json.readTree(Base64.getUrlDecoder().decode(credential.split("\\.")[1]));
    }
    String sign(String header, String body, KeyPair key) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String prefix = encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(body.getBytes(StandardCharsets.UTF_8));
        var signature = Signature.getInstance("SHA256withRSA"); signature.initSign(key.getPrivate());
        signature.update(prefix.getBytes(StandardCharsets.US_ASCII));
        return prefix + "." + encoder.encodeToString(signature.sign());
    }
    String sign(ObjectNode body) throws Exception {
        return sign("{\"alg\":\"RS256\",\"typ\":\"maps-session+jwt\",\"kid\":\"current\"}", body.toString(), pair);
    }
    void inactive(String credential) {
        var error = assertThrows(MapsIdentityException.class, () -> tokens.verify(credential, false));
        assertEquals(MapsIdentityException.Code.INACTIVE, error.code()); assertNull(error.getCause());
    }

    @Test void onlyScopedMinimalCredentialCanResolveThePerson() throws Exception {
        String credential = tokens.issue(user, session);
        var verified = tokens.verify(credential, false);
        assertEquals(user.getId(), verified.subject()); assertEquals(session.getSessionId(), verified.sessionId());
        assertEquals(4, verified.version()); assertEquals(NOW.plusSeconds(900), verified.expiresAt());
        assertEquals(7, body(credential).size()); assertFalse(body(credential).has("phone")); assertFalse(body(credential).has("roles"));
    }
    @Test void accountAndSessionPurposeCannotBePromotedAtIssuance() {
        user.setActive(false); assertThrows(MapsIdentityException.class, () -> tokens.issue(user, session));
        user.setActive(true); session.setPurpose(OtpPurpose.ADMIN);
        assertThrows(MapsIdentityException.class, () -> tokens.issue(user, session));
    }
    @Test void lifetimeIsBoundedByAbsoluteSessionExpiryAndEqualityIsInactive() {
        session.setAbsoluteExpiresAt(NOW.plusSeconds(10));
        String credential = tokens.issue(user, session); assertEquals(NOW.plusSeconds(10), tokens.verify(credential, false).expiresAt());
        when(clock.instant()).thenReturn(NOW.plusSeconds(10)); inactive(credential);
        assertEquals(user.getId(), tokens.verify(credential, true).subject());
    }
    @Test void expiryExceptionCannotBypassSignatureVerificationForLogout() throws Exception {
        String credential = tokens.issue(user, session); when(clock.instant()).thenReturn(NOW.plusSeconds(901));
        var other = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String forged = sign("{\"alg\":\"RS256\",\"typ\":\"maps-session+jwt\",\"kid\":\"current\"}", body(credential).toString(), other);
        assertThrows(MapsIdentityException.class, () -> tokens.verify(forged, true));
        assertNotNull(tokens.verify(credential, true));
    }
    @Test void crossApplicationAndUnknownSecurityFieldsAreRejectedEvenWithTheCorrectSignature() throws Exception {
        String valid = tokens.issue(user, session);
        for (var mutation : List.<java.util.function.Consumer<ObjectNode>>of(
                b -> b.put("iss", "https://other.test"), b -> b.put("aud", "food-delivery"), b -> b.put("ev", "4"),
                b -> b.put("ev", -1), b -> b.put("ev", 4.5), b -> b.putNull("sub"), b -> b.put("sub", "1-1-1-1-1"),
                b -> b.put("session_id", "foreign"), b -> b.put("iat", NOW.plusSeconds(1).getEpochSecond()),
                b -> b.put("exp", NOW.getEpochSecond()), b -> b.put("exp", NOW.plusSeconds(901).getEpochSecond()),
                b -> b.put("roles", "ADMIN"), b -> b.put("nbf", 0))) {
            var b = body(valid); mutation.accept(b); inactive(sign(b));
        }
        String payload = body(valid).toString();
        for (String header : List.of(
                "{\"alg\":\"none\",\"typ\":\"maps-session+jwt\",\"kid\":\"current\"}",
                "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"current\"}",
                "{\"alg\":\"RS256\",\"typ\":\"maps-session+jwt\",\"kid\":\"retired\"}",
                "{\"alg\":\"RS256\",\"typ\":\"maps-session+jwt\",\"kid\":\"current\",\"jku\":\"https://evil.test\"}"))
            inactive(sign(header, payload, pair));
    }
    @Test void signedDuplicateFieldsAndMalformedCompactTokensAreInactive() throws Exception {
        String payload = body(tokens.issue(user, session)).toString();
        inactive(sign("{\"alg\":\"RS256\",\"typ\":\"maps-session+jwt\",\"kid\":\"current\"}",
                payload.replace("\"aud\":", "\"aud\":\"other\",\"aud\":"), pair));
        for (String value : new String[]{null, "", "x.y.z", "a.b", "a.b.c.d", "x".repeat(4097)}) inactive(value);
    }
    @Test void keyConfigurationFailsClosedAndRejectsPackagedOrReadablePrivateKeys() throws Exception {
        properties.setPrivateKey("classpath:certs/private.pem");
        assertThrows(IllegalStateException.class, () -> new MapsSessionTokens(properties, clock, new DefaultResourceLoader()));
        properties = configured(temp, pair);
        Files.setPosixFilePermissions(Path.of(java.net.URI.create(properties.getPrivateKey())), PosixFilePermissions.fromString("rw-r--r--"));
        assertThrows(IllegalStateException.class, () -> new MapsSessionTokens(properties, clock, new DefaultResourceLoader()));
        properties = configured(temp, pair); properties.setCredentialLifetime(Duration.ofHours(1));
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test void configuredOverlapAcceptsBothKeysAndRetirementRefusesTheOldKey() throws Exception {
        String old=tokens.issue(user,session);
        var directory=Files.createDirectory(temp.resolve("rotation"));
        var nextPair=KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var next=configured(directory,nextPair);next.setSigningKeyId("next");
        next.setVerificationKeys(Map.of("current",properties.getVerificationKeys().get("current"),"next",directory.resolve("public.pem").toUri().toString()));
        var rotated=new MapsSessionTokens(next,clock,new DefaultResourceLoader());
        assertEquals(user.getId(),rotated.verify(old,false).subject());
        String fresh=rotated.issue(user,session);assertEquals(user.getId(),rotated.verify(fresh,false).subject());
        next.setVerificationKeys(Map.of("next",directory.resolve("public.pem").toUri().toString()));
        var retired=new MapsSessionTokens(next,clock,new DefaultResourceLoader());
        assertThrows(MapsIdentityException.class,()->retired.verify(old,false));assertNotNull(retired.verify(fresh,false));
    }
}
