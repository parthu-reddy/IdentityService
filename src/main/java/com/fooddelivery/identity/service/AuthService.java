package com.fooddelivery.identity.service;

import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.common.service.NotificationRouterService;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final NotificationRouterService notificationRouterService;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    public void initiateLogin(String phoneNumber, String role) {
        String otp = String.format("%06d", secureRandom.nextInt(999999));
        
        redisTemplate.opsForValue().set("OTP:" + phoneNumber + ":" + role, otp, Duration.ofMinutes(5));

        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .userId(null) 
                .explicitRecipient(phoneNumber)
                .channel(com.fooddelivery.common.enums.ChannelType.SMS)
                .build();
                
        notificationRouterService.routeNotification(event);
        log.info("Initiated login for {}, role {}, OTP generated.", phoneNumber, role);
    }

    public String verifyOtp(String phoneNumber, String otp, String role) {
        String cacheKey = "OTP:" + phoneNumber + ":" + role;
        String cachedOtp = redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedOtp != null && cachedOtp.equals(otp)) {
            redisTemplate.delete(cacheKey);
            
            AppUser user = userRepository.findByPhoneNumberAndRole(phoneNumber, role)
                    .orElseGet(() -> userRepository.save(AppUser.builder()
                            .phoneNumber(phoneNumber)
                            .role(role)
                            .build()));
            
            return generateJwtToken(user);
        }
        throw new IllegalArgumentException("Invalid or expired OTP");
    }

    private String generateJwtToken(AppUser user) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return Jwts.builder()
                .setSubject(user.getId().toString())
                .claim("phone", user.getPhoneNumber())
                .claim("role", user.getRole())
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
