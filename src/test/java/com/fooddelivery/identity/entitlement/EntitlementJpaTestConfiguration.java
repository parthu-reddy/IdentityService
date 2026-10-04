package com.fooddelivery.identity.entitlement;

import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.entitlement.entity.BusinessEntitlement;
import com.fooddelivery.identity.entitlement.repository.BusinessEntitlementRepository;
import com.fooddelivery.identity.entitlement.service.*;
import com.fooddelivery.identity.organisation.entity.Organisation;
import com.fooddelivery.identity.organisation.repository.OrganisationRepository;
import com.fooddelivery.identity.repository.UserRepository;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import java.time.*;

@TestConfiguration @EnableAutoConfiguration
@EntityScan(basePackageClasses={AppUser.class,Organisation.class,BusinessEntitlement.class,com.fooddelivery.common.entity.IdempotencyKey.class})
@EnableJpaRepositories(basePackageClasses={UserRepository.class,OrganisationRepository.class,BusinessEntitlementRepository.class,com.fooddelivery.common.repository.IIdempotencyKeyRepository.class})
@Import({EntitlementProjectionService.class,EntitlementVersionPublisher.class,EntitlementService.class,PortalService.class,
    EntitlementEventProcessor.class})
class EntitlementJpaTestConfiguration {
    @Bean Clock clock(){return Clock.fixed(Instant.parse("2026-10-03T10:00:00Z"),ZoneOffset.UTC);}
    @Bean com.fasterxml.jackson.databind.ObjectMapper mapper(){return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
        .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);}
    @Bean com.fooddelivery.common.event.EventBinder binder(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return new com.fooddelivery.common.event.EventBinder(mapper,jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());
    }
}
