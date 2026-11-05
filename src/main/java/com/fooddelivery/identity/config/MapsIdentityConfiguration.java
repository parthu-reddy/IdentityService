package com.fooddelivery.identity.config;

import com.fooddelivery.identity.filter.MapsIdentityFilter;
import com.fooddelivery.identity.repository.UserRepository;
import com.fooddelivery.identity.security.MapsPeerAuthenticator;
import com.fooddelivery.identity.service.AuthService;
import com.fooddelivery.identity.service.MapsCurrentSessionService;
import com.fooddelivery.identity.service.MapsIdentityOperations;
import com.fooddelivery.identity.service.MapsSessionTokens;
import com.fooddelivery.identity.service.SessionRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Validator;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MapsIdentityProperties.class)
public class MapsIdentityConfiguration {
    @Bean
    @ConditionalOnProperty(name = "identity.maps.enabled", havingValue = "true")
    org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer mapsPrimarySessionReads() {
        return builder -> builder.readFrom(io.lettuce.core.ReadFrom.MASTER);
    }
    @Bean
    @ConditionalOnProperty(name = "identity.maps.enabled", havingValue = "true")
    MapsSessionTokens mapsSessionTokens(MapsIdentityProperties properties, Clock clock,
                                        ResourceLoader resources, Environment environment) {
        // Container-verified certificates are required, never forwarded client certificate text.
        String auth = environment.getProperty("server.ssl.client-auth", "none");
        if (!environment.getProperty("server.ssl.enabled", Boolean.class, false)
                || !"need".equalsIgnoreCase(auth)
                || environment.getProperty("server.ssl.trust-store", "").isBlank())
            throw new IllegalStateException("Maps identity requires a client-authenticated TLS connector and trust store");
        return new MapsSessionTokens(properties, clock, resources);
    }

    @Bean
    @ConditionalOnProperty(name = "identity.maps.enabled", havingValue = "true")
    MapsCurrentSessionService mapsCurrentSessionService(UserRepository users, SessionRegistry sessions,
                                                       MapsSessionTokens tokens, MapsIdentityProperties properties, Clock clock) {
        return new MapsCurrentSessionService(users, sessions, tokens, properties, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "identity.maps.enabled", havingValue = "true")
    MapsIdentityOperations mapsIdentityOperations(AuthService auth, MapsCurrentSessionService current,
                                                 MapsIdentityProperties properties, Validator validator) {
        return new MapsIdentityOperations(auth, current, properties, validator);
    }

    @Bean
    FilterRegistrationBean<MapsIdentityFilter> mapsIdentityTransport(MapsIdentityProperties properties,
            Clock clock, ObjectProvider<MapsIdentityOperations> operations, MeterRegistry metrics) {
        var filter = new MapsIdentityFilter(properties, new MapsPeerAuthenticator(properties, clock),
                operations.getIfAvailable(), metrics);
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setAsyncSupported(true);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
