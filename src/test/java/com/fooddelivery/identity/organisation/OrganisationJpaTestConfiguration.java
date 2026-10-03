package com.fooddelivery.identity.organisation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.audit.*;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.organisation.entity.Organisation;
import com.fooddelivery.identity.organisation.repository.OrganisationRepository;
import com.fooddelivery.identity.organisation.service.*;
import com.fooddelivery.identity.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import java.time.*;

@TestConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses={AppUser.class,Organisation.class,OutboxEventEntity.class,AuditEvent.class})
@EnableJpaRepositories(basePackageClasses={UserRepository.class,OrganisationRepository.class,OutboxEventRepository.class})
@Import({OrganisationService.class,OrganisationInvitationService.class,AuditTrail.class,AuditReader.class})
class OrganisationJpaTestConfiguration {
    @Bean Clock clock(){return Clock.fixed(Instant.parse("2026-10-03T10:00:00Z"),ZoneOffset.UTC);}
    @Bean ObjectMapper objectMapper(){return new ObjectMapper().findAndRegisterModules();}
    @Bean SimpleMeterRegistry meterRegistry(){return new SimpleMeterRegistry();}
}
