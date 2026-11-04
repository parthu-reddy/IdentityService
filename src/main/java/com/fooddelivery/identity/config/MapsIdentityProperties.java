package com.fooddelivery.identity.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** No deployment identity or certificate is inferred from a request or forwarded header. */
@lombok.Data
@ConfigurationProperties("identity.maps")
public class MapsIdentityProperties {
    private boolean enabled;
    private String authority;
    private String audience;
    private String signingKeyId;
    private String privateKey = "classpath:certs/private.pem";
    private String verificationKey;
    private Map<String, String> verificationKeys = Map.of();
    private List<String> allowedPeers = List.of();
    private Duration credentialLifetime = Duration.ofMinutes(15);
    private Duration decisionLifetime = Duration.ofSeconds(1);
    private Duration requestTimeout = Duration.ofSeconds(2);
    private int concurrency = 8;

    public void validate() {
        try {
            URI issuer = URI.create(authority);
            if (!"https".equals(issuer.getScheme()) || issuer.getHost() == null
                    || issuer.getUserInfo() != null || issuer.getRawQuery() != null || issuer.getFragment() != null
                    || authority.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1024)
                throw new IllegalArgumentException();
            if (audience == null || !audience.matches("[A-Za-z0-9:/._-]{1,128}")
                    || signingKeyId == null || !signingKeyId.matches("[A-Za-z0-9_-]{1,64}")
                    || privateKey == null || !privateKey.startsWith("file:")
                    || !effectiveVerificationKeys().containsKey(signingKeyId) || effectiveVerificationKeys().size() > 8
                    || effectiveVerificationKeys().keySet().stream().anyMatch(k -> !k.matches("[A-Za-z0-9_-]{1,64}"))
                    || effectiveVerificationKeys().values().stream().anyMatch(v -> !v.startsWith("file:"))
                    || allowedPeers.isEmpty() || allowedPeers.size() > 16
                    || allowedPeers.stream().anyMatch(p -> p.length() > 256 || !URI.create(p).isAbsolute())
                    || concurrency < 1 || concurrency > 64
                    || credentialLifetime == null || credentialLifetime.isNegative() || credentialLifetime.isZero()
                    || credentialLifetime.compareTo(Duration.ofMinutes(15)) > 0
                    || decisionLifetime == null || decisionLifetime.isNegative() || decisionLifetime.isZero()
                    || decisionLifetime.compareTo(Duration.ofSeconds(1)) > 0
                    || requestTimeout == null || requestTimeout.compareTo(Duration.ofMillis(50)) < 0
                    || requestTimeout.compareTo(Duration.ofSeconds(2)) > 0)
                throw new IllegalArgumentException();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Maps identity configuration is invalid");
        }
    }
    public Map<String, String> effectiveVerificationKeys() {
        var result = new java.util.HashMap<>(verificationKeys);
        if (verificationKey != null && !verificationKey.isBlank()) {
            if (result.containsKey(signingKeyId) && !verificationKey.equals(result.get(signingKeyId)))
                throw new IllegalStateException("Conflicting Maps signing key configuration");
            result.put(signingKeyId, verificationKey);
        }
        return Map.copyOf(result);
    }
}
