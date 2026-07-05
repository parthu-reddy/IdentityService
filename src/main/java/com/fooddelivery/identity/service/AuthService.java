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
        String otp = String.format("%06d", secureRandom.nextInt(999999));
        
        cachePort.put("OTP:" + phoneNumber + ":" + serviceName, otp, 5);

        eventPublisherPort.publishNotificationEvent(phoneNumber, "SMS");
        log.info("Initiated login for {}, service {}, OTP generated.", phoneNumber, serviceName);
    }

    public String verifyOtp(String phoneNumber, String otp, String serviceName) {
        String cacheKey = "OTP:" + phoneNumber + ":" + serviceName;
        String cachedOtp = cachePort.get(cacheKey);
        
        if (cachedOtp != null && cachedOtp.equals(otp)) {
            cachePort.delete(cacheKey);
            
            AppUser user = userRepository.findByPhoneNumber(phoneNumber)
                    .orElseGet(() -> userRepository.save(AppUser.builder()
                            .phoneNumber(phoneNumber)
                            .build()));
                            
            if ("CUSTOMER_APP".equals(serviceName)) {
                List<UserRole> existingRoles = userRoleRepository.findByUserIdAndServiceName(user.getId(), serviceName);
                if (existingRoles.isEmpty()) {
                    userRoleRepository.save(UserRole.builder()
                        .user(user)
                        .serviceName(serviceName)
                        .roleName("CUSTOMER")
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
