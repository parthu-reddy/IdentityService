package com.fooddelivery.identity.config;

import com.fooddelivery.identity.controller.DevOtpController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/** Allows only the exact Dev lookup; normal session and account authorization remain intact. */
@Configuration(proxyBeanMethods = false)
@Profile("dev & !prod")
@ConditionalOnProperty(prefix = "dev.otp", name = "enabled", havingValue = "true")
public class DevOtpSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain devOtpSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(new AntPathRequestMatcher(DevOtpController.OTP_LOOKUP_PATH, "GET"))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }
}
