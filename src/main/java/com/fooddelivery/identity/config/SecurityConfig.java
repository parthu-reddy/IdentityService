package com.fooddelivery.identity.config;

import com.fooddelivery.common.security.CommonSecurityConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(CommonSecurityConfig.class)
public class SecurityConfig {
    // CommonSecurityConfig already secures all endpoints by default,
    // and permits /api/v1/internal/auth/**.
    // For /api/v1/internal/users/**, they will require authentication because they are not in the permitAll list.
}
