package com.fooddelivery.identity.service;

import com.fooddelivery.identity.port.CachePort;
import com.fooddelivery.identity.port.EventPublisherPort;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.entity.UserRole;
import com.fooddelivery.identity.repository.UserRepository;
import com.fooddelivery.identity.repository.UserRoleRepository;
import com.fooddelivery.identity.dto.SessionInfo;
import com.fooddelivery.identity.exception.MaxSessionsReachedException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.stream.Collectors;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;
import jakarta.annotation.PostConstruct;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final EventPublisherPort eventPublisherPort;
    private final CachePort cachePort;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    
    // Redis based rate limiting will be used via CachePort

    @Value("${jwt.private-key.path:classpath:certs/private.pem}")
    private Resource privateKeyResource;
    
    @Value("${jwt.public-key.path:classpath:certs/public.pem}")
    private Resource publicKeyResource;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;
    
    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            byte[] keyBytes = FileCopyUtils.copyToByteArray(privateKeyResource.getInputStream());
            String keyString = new String(keyBytes, StandardCharsets.UTF_8)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            
            byte[] decodedKey = Base64.getDecoder().decode(keyString);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.privateKey = keyFactory.generatePrivate(keySpec);
            
            if (publicKeyResource.exists()) {
                byte[] pubKeyBytes = FileCopyUtils.copyToByteArray(publicKeyResource.getInputStream());
                String pubKeyString = new String(pubKeyBytes, StandardCharsets.UTF_8)
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");
                byte[] decodedPubKey = Base64.getDecoder().decode(pubKeyString);
                X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(decodedPubKey);
                this.publicKey = keyFactory.generatePublic(pubKeySpec);
            }
        } catch (Exception e) {
            log.error("Failed to load RSA keys", e);
            throw new RuntimeException("Could not load RSA keys", e);
        }
    }

    public void initiateLogin(String phoneNumber, String serviceName) {
        String rateLimitKey = "RATELIMIT:INITIATE:" + phoneNumber;
        Long attempts = cachePort.increment(rateLimitKey, 10);
        
        if (attempts != null && attempts > 3) {
            log.warn("Rate limit exceeded for phone number: {}", phoneNumber);
            throw new IllegalArgumentException("Too many login attempts. Please try again later.");
        }

        String otp = String.format("%06d", secureRandom.nextInt(1000000));
        
        String normalizedServiceName = serviceName != null ? serviceName.toLowerCase() : "customer";
        cachePort.put("OTP:" + phoneNumber + ":" + normalizedServiceName, otp, 5);

        eventPublisherPort.publishNotificationEvent(phoneNumber, "SMS", otp);
        log.info("Initiated login for {}, service {}, OTP generated.", phoneNumber, serviceName);
    }

    @Transactional
    public String verifyOtp(String phoneNumber, String otp, String serviceName, String deviceInfo, String os, String browser, String removeSessionId) {
        String rateLimitKey = "RATELIMIT:VERIFY:" + phoneNumber;
        Long attempts = cachePort.increment(rateLimitKey, 5);
        
        if (attempts != null && attempts > 5) {
            log.warn("Brute force attempt detected for phone number: {}", phoneNumber);
            cachePort.delete("OTP:" + phoneNumber + ":" + serviceName); // Invalidate the OTP
            throw new IllegalArgumentException("Too many failed attempts. Please request a new OTP.");
        }

        String normalizedServiceName = serviceName != null ? serviceName.toLowerCase() : "customer";
        String cacheKey = "OTP:" + phoneNumber + ":" + normalizedServiceName;
        String cachedOtp = cachePort.get(cacheKey);
        
        if (cachedOtp != null && cachedOtp.equals(otp)) {
            AppUser user = userRepository.findByPhoneNumber(phoneNumber)
                    .orElseGet(() -> userRepository.save(AppUser.builder()
                            .phoneNumber(phoneNumber)
                            .build()));

            List<SessionInfo> activeSessions = getActiveSessions(user.getId());
            
            if (removeSessionId != null && !removeSessionId.isEmpty()) {
                activeSessions.removeIf(session -> session.getSessionId().equals(removeSessionId));
                cachePort.put("BLACKLIST:SESSION:" + removeSessionId, "true", jwtExpirationMs / 60000);
            }
            
            List<SessionInfo> serviceSessions = activeSessions.stream()
                .filter(s -> normalizedServiceName.equals(s.getServiceName()))
                .collect(Collectors.toList());

            // Auto-replace stale sessions from the same device (same OS + browser).
            // This handles the case where a user's browser crashed or cookies were cleared —
            // the old session is orphaned but still counted. We replace it silently.
            if (serviceSessions.size() >= 2 && os != null && browser != null) {
                String deviceFingerprint = os + "|" + browser;
                SessionInfo staleSession = serviceSessions.stream()
                        .filter(s -> (s.getOs() + "|" + s.getBrowser()).equals(deviceFingerprint))
                        .findFirst()
                        .orElse(null);
                
                if (staleSession != null) {
                    log.info("Auto-replacing stale session {} from same device ({}) for user {}", 
                            staleSession.getSessionId(), deviceFingerprint, user.getId());
                    activeSessions.removeIf(s -> s.getSessionId().equals(staleSession.getSessionId()));
                    cachePort.put("BLACKLIST:SESSION:" + staleSession.getSessionId(), "true", jwtExpirationMs / 60000);
                    // Recalculate after removal
                    serviceSessions = activeSessions.stream()
                        .filter(s -> normalizedServiceName.equals(s.getServiceName()))
                        .collect(Collectors.toList());
                }
            }

            if (serviceSessions.size() >= 2) {
                // DO NOT delete OTP or rate limit here so user can immediately retry with a removeSessionId
                throw new MaxSessionsReachedException("Maximum concurrent sessions reached", serviceSessions);
            }

            cachePort.delete(cacheKey);
            cachePort.delete(rateLimitKey); // Reset verification attempts on success
                            
            String defaultRoleName = null;
            if (serviceName != null) {
                String normalized = serviceName.toUpperCase();
                if (normalized.contains("CUSTOMER")) {
                    defaultRoleName = "CUSTOMER";
                } else if (normalized.contains("DELIVERY")) {
                    defaultRoleName = "DELIVERY";
                } else if (normalized.contains("RESTAURANT")) {
                    defaultRoleName = "RESTAURANT";
                } else if (normalized.contains("ADMIN")) {
                    defaultRoleName = "ADMIN";
                }
            }
            
            List<String> finalRoleNames = new ArrayList<>();
            if (defaultRoleName != null) {
                List<UserRole> existingRoles = userRoleRepository.findByUserIdAndServiceName(user.getId(), serviceName);
                if (existingRoles.isEmpty()) {
                    userRoleRepository.save(UserRole.builder()
                        .user(user)
                        .serviceName(serviceName)
                        .roleName(defaultRoleName)
                        .build());
                    finalRoleNames.add(defaultRoleName);
                } else {
                    finalRoleNames = existingRoles.stream().map(UserRole::getRoleName).collect(Collectors.toList());
                }
            } else {
                List<UserRole> existingRoles = userRoleRepository.findByUserIdAndServiceName(user.getId(), serviceName);
                finalRoleNames = existingRoles.stream().map(UserRole::getRoleName).collect(Collectors.toList());
            }
            
            String newSessionId = UUID.randomUUID().toString();
            activeSessions.add(SessionInfo.builder()
                    .sessionId(newSessionId)
                    .deviceInfo(deviceInfo != null ? deviceInfo : "Unknown Device")
                    .os(os != null ? os : "Unknown OS")
                    .browser(browser != null ? browser : "Unknown Browser")
                    .lastActive(System.currentTimeMillis())
                    .serviceName(normalizedServiceName)
                    .build());
                    
            saveActiveSessions(user.getId(), activeSessions);
            
            return generateJwtToken(user, finalRoleNames, newSessionId);
        }
        throw new IllegalArgumentException("Invalid or expired OTP");
    }

    private List<SessionInfo> getActiveSessions(UUID userId) {
        String sessionsJson = cachePort.get("USER_SESSIONS:" + userId);
        if (sessionsJson != null) {
            try {
                return objectMapper.readValue(sessionsJson, new TypeReference<List<SessionInfo>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse sessions for user {}", userId, e);
            }
        }
        return new ArrayList<>();
    }
    
    private void saveActiveSessions(UUID userId, List<SessionInfo> sessions) {
        try {
            String json = objectMapper.writeValueAsString(sessions);
            cachePort.put("USER_SESSIONS:" + userId, json, jwtExpirationMs / 60000);
        } catch (Exception e) {
            log.warn("Failed to save sessions for user {}", userId, e);
        }
    }

    public void logout(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
                    
            String userIdStr = claims.getSubject();
            String sessionId = claims.get("sessionId", String.class);
            
            if (userIdStr != null && sessionId != null) {
                UUID userId = UUID.fromString(userIdStr);
                removeSession(userId, sessionId);
            }
        } catch (Exception e) {
            log.warn("Failed to parse token for logout", e);
        }
    }

    public List<SessionInfo> getUserSessions(UUID userId) {
        return getActiveSessions(userId);
    }

    public void removeSession(UUID userId, String sessionId) {
        List<SessionInfo> activeSessions = getActiveSessions(userId);
        boolean removed = activeSessions.removeIf(session -> session.getSessionId().equals(sessionId));
        if (removed) {
            saveActiveSessions(userId, activeSessions);
            cachePort.put("BLACKLIST:SESSION:" + sessionId, "true", jwtExpirationMs / 60000);
        }
    }

    public void removeAllSessions(UUID userId) {
        List<SessionInfo> activeSessions = getActiveSessions(userId);
        for (SessionInfo session : activeSessions) {
            cachePort.put("BLACKLIST:SESSION:" + session.getSessionId(), "true", jwtExpirationMs / 60000);
        }
        activeSessions.clear();
        saveActiveSessions(userId, activeSessions);
    }

    private String generateJwtToken(AppUser user, List<String> roleNames, String sessionId) {

        return Jwts.builder()
                .setSubject(user.getId().toString())
                .claim("phone", user.getPhoneNumber())
                .claim("roles", roleNames)
                .claim("sessionId", sessionId)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }
}
