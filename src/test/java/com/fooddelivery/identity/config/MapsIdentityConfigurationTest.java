package com.fooddelivery.identity.config;

import io.lettuce.core.ReadFrom;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class MapsIdentityConfigurationTest {
    @Test void enablingMapsCannotUseAnUnverifiedTlsConnector() {
        var configuration=new MapsIdentityConfiguration();
        for(var environment:java.util.List.of(new MockEnvironment(),
                new MockEnvironment().withProperty("server.ssl.enabled","true").withProperty("server.ssl.client-auth","want"),
                new MockEnvironment().withProperty("server.ssl.enabled","true").withProperty("server.ssl.client-auth","need"))) {
            assertThrows(IllegalStateException.class,()->configuration.mapsSessionTokens(new MapsIdentityProperties(),Clock.systemUTC(),new DefaultResourceLoader(),environment));
        }
    }
    @Test void mapsProfileSuppressesRawSqlAndBindFailuresAsActualLogLevels() throws Exception {
        var environment=new org.springframework.core.env.StandardEnvironment();
        for(var source:new org.springframework.boot.env.YamlPropertySourceLoader().load("maps",
                new org.springframework.core.io.ClassPathResource("application-maps.yml")))
            environment.getPropertySources().addLast(source);
        var levels=org.springframework.boot.context.properties.bind.Binder.get(environment).bind("logging.level",
                org.springframework.boot.context.properties.bind.Bindable.mapOf(String.class,org.springframework.boot.logging.LogLevel.class)).orElseThrow(IllegalStateException::new);
        for(String logger:java.util.List.of("org.hibernate.SQL","org.hibernate.orm.jdbc.bind",
                "org.hibernate.engine.jdbc.spi.SqlExceptionHelper","org.hibernate.event.internal.DefaultLoadEventListener"))
            assertEquals(org.springframework.boot.logging.LogLevel.OFF,levels.get(logger));
    }
    @Test void healthOnTheLoopbackPortNeedsNoClientCertificate() throws Exception {
        var environment=new org.springframework.core.env.StandardEnvironment();
        for(var source:new org.springframework.boot.env.YamlPropertySourceLoader().load("maps",
                new org.springframework.core.io.ClassPathResource("application-maps.yml")))
            environment.getPropertySources().addLast(source);
        assertEquals("127.0.0.1",environment.getProperty("management.server.address"));
        assertEquals("false",environment.getProperty("management.server.ssl.enabled"));
    }
    @Test void mapsSessionReadsAreExplicitlyPrimaryOnly() {
        var builder=LettuceClientConfiguration.builder().readFrom(ReadFrom.REPLICA_PREFERRED);
        new MapsIdentityConfiguration().mapsPrimarySessionReads().customize(builder);
        assertEquals(ReadFrom.MASTER,builder.build().getReadFrom().orElseThrow());
    }
}
