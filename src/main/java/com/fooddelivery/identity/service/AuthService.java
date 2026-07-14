package com.fooddelivery.identity.service;

import com.fooddelivery.identity.port.CachePort;
import com.fooddelivery.identity.port.EventPublisherPort;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.entity.UserRole;
import com.fooddelivery.identity.repository.UserRepository;
import com.fooddelivery.identity.repository.UserRoleRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.stream.Collectors;
import java.util.Date;
import java.util.List;
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
    private final SecureRandom secureRandom = new SecureRandom();
    
    // Redis based rate limiting will be used via CachePort

    @Value("${jwt.private-key.path:classpath:certs/private.pem}")
    private Resource privateKeyResource;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;
    
    private PrivateKey privateKey;

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
        } catch (Exception e) {
            log.error("Failed to load RSA private key", e);
            throw new RuntimeException("Could not load RSA private key", e);
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
    public String verifyOtp(String phoneNumber, String otp, String serviceName) {
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
            cachePort.delete(cacheKey);
            cachePort.delete(rateLimitKey); // Reset verification attempts on success
            
            AppUser user = userRepository.findByPhoneNumber(phoneNumber)
                    .orElseGet(() -> userRepository.save(AppUser.builder()
                            .phoneNumber(phoneNumber)
                            .build()));
                            
            String defaultRoleName = null;
            if (serviceName != null) {
                String normalized = serviceName.toUpperCase();
                if (normalized.contains("CUSTOMER")) {
                    defaultRoleName = "CUSTOMER";
                } else if (normalized.contains("DELIVERY")) {
                    defaultRoleName = "DELIVERY";
                } else if (normalized.contains("RESTAURANT")) {
                    defaultRoleName = "RESTAURANT";
                }
            }
            
            if (defaultRoleName != null) {
                List<UserRole> existingRoles = userRoleRepository.findByUserIdAndServiceName(user.getId(), serviceName);
                if (existingRoles.isEmpty()) {
                    userRoleRepository.save(UserRole.builder()
                        .user(user)
                        .serviceName(serviceName)
                        .roleName(defaultRoleName)
                        .build());
                }
            }
            
            return generateJwtToken(user, serviceName);
        }
        throw new IllegalArgumentException("Invalid or expired OTP");
    }

    private String generateJwtToken(AppUser user, String serviceName) {
        List<UserRole> roles = userRoleRepository.findByUserIdAndServiceName(user.getId(), serviceName);
        List<String> roleNames = roles.stream().map(UserRole::getRoleName).collect(Collectors.toList());

        return Jwts.builder()
                .setSubject(user.getId().toString())
                .claim("phone", user.getPhoneNumber())
                .claim("roles", roleNames)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }
}
