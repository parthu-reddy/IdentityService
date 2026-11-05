package com.fooddelivery.identity.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.audit.AuditTrail;
import com.fooddelivery.identity.adapter.RedisCacheAdapter;
import com.fooddelivery.identity.config.MapsIdentityProperties;
import com.fooddelivery.identity.entitlement.service.EntitlementService;
import com.fooddelivery.identity.port.EventPublisherPort;
import com.fooddelivery.identity.repository.UserRepository;
import com.fooddelivery.identity.service.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.lettuce.core.resource.DefaultClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.mockito.Mockito.*;

/** Owned test composition: real authority stores/transactions, isolated notification capture. */
final class MapsNativeAuthority implements AutoCloseable {
    record Configuration(String jdbc_url, String username, String password, int redis_port, String redis_password, String runtime_password) {}
    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false) @EnableTransactionManagement
    static class Transactions {}
    final AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext();
    final AtomicReference<String> delivered=new AtomicReference<>();
    final HikariDataSource datasource;
    final JdbcTemplate operator;
    final DefaultClientResources resources;
    final LettuceConnectionFactory connections;
    final StringRedisTemplate redis;
    MapsNativeAuthority(Path path, MapsIdentityProperties properties, MapsSessionTokens tokens, Clock clock,
                        ObjectMapper mapper, MeterRegistry metrics) throws Exception {
        if(!Files.isRegularFile(path) || Files.size(path)>8192 || Files.getPosixFilePermissions(path).stream().anyMatch(p->p==PosixFilePermission.OTHERS_READ || p==PosixFilePermission.OTHERS_WRITE))
            throw new IllegalArgumentException("Owned native config unavailable");
        var config=mapper.readValue(Files.readAllBytes(path),Configuration.class);
        if(!config.jdbc_url().startsWith("jdbc:postgresql://127.0.0.1:") || config.redis_port()<1 || config.redis_port()>65535)
            throw new IllegalArgumentException("Owned loopback fixture required");
        var migration=new org.springframework.jdbc.datasource.DriverManagerDataSource(config.jdbc_url(),config.username(),config.password());
        operator=new JdbcTemplate(migration);
        Flyway.configure().dataSource(migration).locations("classpath:db/migration").load().migrate();
        if(config.runtime_password()==null || !config.runtime_password().matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Owned runtime credential required");
        operator.execute("CREATE ROLE identity_runtime LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '"+config.runtime_password()+"'");
        operator.execute("GRANT SELECT,INSERT,UPDATE ON users TO identity_runtime");
        operator.execute("GRANT SELECT ON user_roles TO identity_runtime");
        var hikari=new HikariConfig();hikari.setJdbcUrl(config.jdbc_url());hikari.setUsername("identity_runtime");hikari.setPassword(config.runtime_password());
        hikari.setMaximumPoolSize(4);hikari.setMinimumIdle(0);hikari.setConnectionTimeout(250);hikari.setValidationTimeout(250);
        datasource=new HikariDataSource(hikari);
        resources=DefaultClientResources.builder().ioThreadPoolSize(2).computationThreadPoolSize(2).build();
        var standalone=new RedisStandaloneConfiguration("127.0.0.1",config.redis_port());standalone.setPassword(config.redis_password());
        var lettuce=LettuceClientConfiguration.builder().clientResources(resources).commandTimeout(Duration.ofMillis(350)).shutdownTimeout(Duration.ofMillis(350)).build();
        connections=new LettuceConnectionFactory(standalone,lettuce);
        try {
            connections.afterPropertiesSet();connections.start();redis=new StringRedisTemplate(connections);redis.afterPropertiesSet();
            context.getBeanFactory().setConversionService(org.springframework.boot.convert.ApplicationConversionService.getSharedInstance());
            context.register(Transactions.class);
            context.registerBean("entityManagerFactory",LocalContainerEntityManagerFactoryBean.class,()->{
                var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(datasource);
                factory.setPackagesToScan("com.fooddelivery.identity");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy","hibernate.cache.use_second_level_cache","false","hibernate.cache.use_query_cache","false"));return factory;
            });
            context.registerBean("transactionManager",JpaTransactionManager.class,()->new JpaTransactionManager(context.getBean(EntityManagerFactory.class)));
            context.registerBean(UserRepository.class,()->new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class))).getRepository(UserRepository.class));
            context.registerBean(Clock.class,()->clock);context.registerBean(ObjectMapper.class,()->mapper);
            context.registerBean(MeterRegistry.class,()->metrics);context.registerBean(MapsSessionTokens.class,()->tokens);
            context.registerBean(MapsIdentityProperties.class,()->properties);
            context.registerBean(com.fooddelivery.identity.port.CachePort.class,()->new RedisCacheAdapter(redis));
            context.registerBean(EventPublisherPort.class,()->{
                var events=mock(EventPublisherPort.class);
                doAnswer(call->{
                    String otp=call.getArgument(2);delivered.set(otp);
                    String capture=System.getenv("MAPS_GO_OTP_DELIVERY");
                    if(capture!=null) Files.writeString(Path.of(capture),otp);
                    return null;
                }).when(events).publishNotificationEvent(anyString(),eq("SMS"),anyString());return events;
            });
            context.registerBean(EntitlementService.class,()->mock(EntitlementService.class));
            context.registerBean(AuditTrail.class,()->mock(AuditTrail.class));
            context.registerBean(UserProvisioner.class);context.registerBean(SessionRegistry.class);context.registerBean(AuthService.class);context.registerBean(MapsCurrentSessionService.class);
            context.refresh();
        } catch(Exception failure) { close(); throw failure; }
    }
    MapsCurrentSessionService current(){return context.getBean(MapsCurrentSessionService.class);}
    AuthService auth(){return context.getBean(AuthService.class);}
    String deliveredOtp(){return delivered.get();}
    StringRedisTemplate redis(){return redis;}
    JdbcTemplate jdbc(){return operator;}
    JdbcTemplate runtimeJdbc(){return new JdbcTemplate(datasource);}
    void stopRedisClient(){connections.destroy();}
    public void close(){context.close();connections.destroy();resources.shutdown();datasource.close();}
}
