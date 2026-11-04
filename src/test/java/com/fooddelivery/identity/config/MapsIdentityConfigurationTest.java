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
    @Test void mapsSessionReadsAreExplicitlyPrimaryOnly() {
        var builder=LettuceClientConfiguration.builder().readFrom(ReadFrom.REPLICA_PREFERRED);
        new MapsIdentityConfiguration().mapsPrimarySessionReads().customize(builder);
        assertEquals(ReadFrom.MASTER,builder.build().getReadFrom().orElseThrow());
    }
}
