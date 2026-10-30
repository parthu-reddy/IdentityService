package com.fooddelivery.identity.dinein;

import com.fooddelivery.identity.dinein.port.DineInConsent;
import com.fooddelivery.identity.dinein.port.DineInConsentStore;
import java.time.Duration;
import java.util.*;

/** The Redis store's contract in memory: attempts count only while the consent exists; consume succeeds once. */
public class InMemoryDineInConsentStore implements DineInConsentStore {
    public final Map<UUID, DineInConsent> consents = new HashMap<>();
    public final Map<UUID, Long> attempts = new HashMap<>();
    public final Map<UUID, Duration> retention = new HashMap<>();

    @Override public void save(DineInConsent consent, Duration keep) {
        consents.put(consent.consentId(), consent);
        attempts.put(consent.consentId(), 0L);
        retention.put(consent.consentId(), keep);
    }
    @Override public Optional<DineInConsent> find(UUID id) { return Optional.ofNullable(consents.get(id)); }
    @Override public long incrementAttempts(UUID id) {
        if (!consents.containsKey(id)) return -1;
        return attempts.merge(id, 1L, Long::sum);
    }
    @Override public boolean consume(UUID id) { attempts.remove(id); return consents.remove(id) != null; }
}
