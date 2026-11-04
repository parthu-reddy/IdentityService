package com.fooddelivery.identity.security;

import com.fooddelivery.identity.config.MapsIdentityProperties;
import com.fooddelivery.identity.exception.MapsIdentityException;
import jakarta.servlet.http.HttpServletRequest;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Date;
import java.util.List;

/** The TLS connector validates the chain; this boundary authorizes the exact URI SAN and EKU. */
public final class MapsPeerAuthenticator {
    private final MapsIdentityProperties properties;
    private final Clock clock;
    public MapsPeerAuthenticator(MapsIdentityProperties properties, Clock clock) {
        this.properties = properties; this.clock = clock;
    }
    public void require(HttpServletRequest request) {
        try {
            if (!request.isSecure()
                    || !(request.getAttribute("jakarta.servlet.request.X509Certificate") instanceof X509Certificate[] chain)
                    || chain.length == 0) throw new IllegalArgumentException();
            X509Certificate leaf = chain[0];
            leaf.checkValidity(Date.from(clock.instant()));
            var usage = leaf.getExtendedKeyUsage();
            if (usage == null || !usage.contains("1.3.6.1.5.5.7.3.2")) throw new IllegalArgumentException();
            var names = leaf.getSubjectAlternativeNames();
            if (names == null || names.stream().noneMatch(s -> s.size() == 2 && Integer.valueOf(6).equals(s.get(0))
                    && s.get(1) instanceof String uri && properties.getAllowedPeers().contains(uri)))
                throw new IllegalArgumentException();
            // Never accept the legacy mesh tuple or a proxy-asserted certificate as peer authority.
            for (String header : List.of("Authorization", "Cookie", "X-User-Id", "X-User-Phone", "X-User-Roles",
                    "X-Session-Id", "X-Identity-Signature", "X-Issued-At", "X-Forwarded-Client-Cert"))
                if (request.getHeader(header) != null) throw new IllegalArgumentException();
        } catch (Exception ex) {
            throw new MapsIdentityException(MapsIdentityException.Code.PEER_DENIED);
        }
    }
}
