package com.fooddelivery.identity.service;

import com.fooddelivery.identity.config.MapsIdentityProperties;
import com.fooddelivery.identity.exception.MapsIdentityException;
import com.fooddelivery.identity.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import static com.fooddelivery.identity.exception.MapsIdentityException.Code.INACTIVE;

/** Current DB account/version plus the owning persisted person-session/revocation authority. */
public class MapsCurrentSessionService {
    private final UserRepository users;
    private final SessionRegistry sessions;
    private final MapsSessionTokens tokens;
    private final MapsIdentityProperties properties;
    private final Clock clock;

    public MapsCurrentSessionService(UserRepository users, SessionRegistry sessions, MapsSessionTokens tokens,
                                     MapsIdentityProperties properties, Clock clock) {
        this.users = users; this.sessions = sessions; this.tokens = tokens; this.properties = properties; this.clock = clock;
    }

    @Transactional(readOnly = true, timeout = 1)
    public Map<String, Object> validate(String credential, String nonce) {
        var token = tokens.verify(credential, false);
        if (!users.mapsAuthorityIsPrimary())
            throw new MapsIdentityException(MapsIdentityException.Code.UNAVAILABLE);
        var user = users.findById(token.subject()).filter(u -> u.isActive() && u.getEntitlementsVersion() == token.version())
                .orElseThrow(() -> new MapsIdentityException(INACTIVE));
        var session = sessions.require(user.getId(), token.sessionId());
        if (session.getPurpose() != OtpPurpose.LOGIN || token.expiresAt().isAfter(session.getAbsoluteExpiresAt())
                || token.issuedAt().isBefore(session.getCreatedAt().truncatedTo(ChronoUnit.SECONDS)))
            throw new MapsIdentityException(INACTIVE);
        Instant checked = clock.instant();
        Instant validUntil = checked.plus(properties.getDecisionLifetime());
        if (token.expiresAt().isBefore(validUntil)) validUntil = token.expiresAt();
        if (!validUntil.isAfter(checked)) throw new MapsIdentityException(INACTIVE);
        return Map.ofEntries(Map.entry("version", 1), Map.entry("nonce", nonce), Map.entry("active", true),
                Map.entry("authority", properties.getAuthority()), Map.entry("audience", properties.getAudience()),
                Map.entry("subject", token.subject().toString()), Map.entry("session_id", token.sessionId()),
                Map.entry("issued_at", token.issuedAt().toString()), Map.entry("expires_at", token.expiresAt().toString()),
                Map.entry("checked_at", checked.toString()), Map.entry("valid_until", validUntil.toString()),
                Map.entry("authentication", Map.of("mfa", false, "methods", java.util.List.of("otp"),
                        "authenticated_at", session.getCreatedAt().toString())));
    }
}
