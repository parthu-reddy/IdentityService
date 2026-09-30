package com.fooddelivery.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Validates the credential used only by the external Java E2E runner.
 *
 * <p>This deliberately does not reuse {@code IDENTITY_HMAC_SECRET}. The mesh credential grants
 * service-to-service identity and is present in every application container; this credential is
 * injected only into the gateway and IdentityService when the {@code e2e} profile is explicitly
 * active.</p>
 */
@Component
@Profile("e2e")
@ConditionalOnProperty(prefix = "e2e.otp", name = "enabled", havingValue = "true")
public final class E2eRunnerSecretVerifier {

    public static final String HEADER_NAME = "X-E2E-Runner-Secret";

    private final byte[] expectedDigest;

    public E2eRunnerSecretVerifier(@Value("${e2e.runner.secret:}") String runnerSecret) {
        if (runnerSecret == null || runnerSecret.isBlank()) {
            throw new IllegalStateException(
                    "e2e.runner.secret must be configured when the e2e profile is active");
        }
        expectedDigest = digest(runnerSecret);
    }

    /**
     * Rejects missing and duplicated headers. Hashing both values makes the final comparison a
     * fixed-length, constant-time comparison even when a caller supplies a differently sized
     * value.
     */
    public boolean matches(List<String> suppliedSecrets) {
        return suppliedSecrets != null
                && suppliedSecrets.size() == 1
                && matches(suppliedSecrets.get(0));
    }

    public boolean matches(String suppliedSecret) {
        if (suppliedSecret == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedDigest, digest(suppliedSecret));
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for E2E runner authentication", exception);
        }
    }
}
