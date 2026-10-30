package com.fooddelivery.identity.dinein.adapter;

import com.fooddelivery.identity.dinein.port.DineInConsent;
import com.fooddelivery.identity.dinein.port.DineInConsentStore;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** One Redis hash per consent; the attempt counter is a field of it, so it expires with the consent. */
@Component
@lombok.RequiredArgsConstructor
public class RedisDineInConsentStore implements DineInConsentStore {
    private static final String PREFIX = "DINEIN:CONSENT:";
    private static final DefaultRedisScript<Long> SAVE =
            new DefaultRedisScript<>(
                    "redis.call('DEL',KEYS[1]);redis.call('HSET',KEYS[1],unpack(ARGV,2));"
                            + "redis.call('PEXPIRE',KEYS[1],ARGV[1]);return 1",
                    Long.class);
    // HINCRBY on a missing key would create a hash with no TTL, so count only while the consent exists.
    private static final DefaultRedisScript<Long> INCREMENT =
            new DefaultRedisScript<>(
                    "if redis.call('EXISTS',KEYS[1])==1 then"
                            + " return redis.call('HINCRBY',KEYS[1],'attempts',1) end;return -1",
                    Long.class);

    private final StringRedisTemplate redis;

    @Override
    public void save(DineInConsent consent, Duration retention) {
        redis.execute(
                SAVE,
                List.of(key(consent.consentId())),
                Long.toString(retention.toMillis()),
                "phone", consent.phone(),
                "outletId", consent.outletId().toString(),
                "sessionId", consent.sessionId().toString(),
                "requestedByUserId", consent.requestedByUserId().toString(),
                "code", consent.code(),
                "expiresAt", consent.expiresAt().toString(),
                "attempts", "0");
    }

    @Override
    public Optional<DineInConsent> find(UUID consentId) {
        Map<Object, Object> h = redis.opsForHash().entries(key(consentId));
        if (h.isEmpty()) return Optional.empty();
        return Optional.of(
                new DineInConsent(
                        consentId,
                        (String) h.get("phone"),
                        UUID.fromString((String) h.get("outletId")),
                        UUID.fromString((String) h.get("sessionId")),
                        UUID.fromString((String) h.get("requestedByUserId")),
                        (String) h.get("code"),
                        Instant.parse((String) h.get("expiresAt"))));
    }

    @Override
    public long incrementAttempts(UUID consentId) {
        Long attempts = redis.execute(INCREMENT, List.of(key(consentId)));
        if (attempts == null) throw new IllegalStateException("Consent store is unavailable");
        return attempts;
    }

    @Override
    public boolean consume(UUID consentId) {
        return Boolean.TRUE.equals(redis.delete(key(consentId)));
    }

    private static String key(UUID consentId) {
        return PREFIX + consentId;
    }
}
