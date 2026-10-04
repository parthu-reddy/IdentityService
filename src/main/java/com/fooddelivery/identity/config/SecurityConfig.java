package com.fooddelivery.identity.config;

import com.fooddelivery.common.security.CommonSecurityConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(CommonSecurityConfig.class)
public class SecurityConfig {
    // Shared default deny; only the exact POST entry points have a separate public chain.
    @org.springframework.context.annotation.Bean
    @org.springframework.core.annotation.Order(2)
    org.springframework.security.web.SecurityFilterChain loginSecurityFilterChain(
            org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
        var matcher=new org.springframework.security.web.util.matcher.OrRequestMatcher(
            new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/v1/auth/otp","POST"),
            new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/v1/auth/session","POST"));
        return http.securityMatcher(matcher)
            .csrf(org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer::disable)
            .cors(org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
    }
}
