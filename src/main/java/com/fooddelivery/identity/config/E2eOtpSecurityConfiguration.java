package com.fooddelivery.identity.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Gives the e2e-only controller a narrow security chain. Its dedicated runner credential is
 * checked by the controller, and this chain does not loosen the normal service chain.
 */
@Configuration(proxyBeanMethods = false)
@Profile("e2e")
@ConditionalOnProperty(prefix = "e2e.otp", name = "enabled", havingValue = "true")
public class E2eOtpSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain e2eOtpSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/v1/internal/e2e/auth/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }
}
