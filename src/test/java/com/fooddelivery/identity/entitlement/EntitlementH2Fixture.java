package com.fooddelivery.identity.entitlement;

import com.fooddelivery.common.enums.*;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.entity.UserRole;
import com.fooddelivery.identity.entitlement.entity.*;
import com.fooddelivery.identity.entitlement.repository.*;
import com.fooddelivery.identity.entitlement.service.EntitlementService;
import com.fooddelivery.identity.organisation.entity.*;
import com.fooddelivery.identity.organisation.repository.*;
import com.fooddelivery.identity.port.CachePort;
import com.fooddelivery.identity.repository.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties={"spring.cloud.config.enabled=false","spring.flyway.enabled=false","spring.cloud.discovery.enabled=false",
    "eureka.client.enabled=false","spring.datasource.url=jdbc:h2:mem:o4_roles;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=EntitlementJpaTestConfiguration.class)
abstract class EntitlementH2Fixture {
    @Autowired EntitlementService entitlements;
    @Autowired UserRepository users; @Autowired UserRoleRepository staff;
    @Autowired OrganisationRepository organisations; @Autowired OrganisationMemberRepository members;
    @Autowired BusinessEntitlementRepository businesses; @Autowired DeliveryEntitlementRepository delivery;
    @Autowired EntityManager em; @MockBean CachePort cache;
    final Instant now=Instant.parse("2026-10-03T10:00:00Z");
    AppUser person() {return users.saveAndFlush(AppUser.builder().phoneNumber("89"+String.format("%08d",Math.abs(UUID.randomUUID().hashCode())%100000000)).isActive(true).build());}
    Organisation organisation(AppUser person,OrganisationStatus status,MembershipStatus membership) {
        var org=organisations.saveAndFlush(Organisation.builder().id(UUID.randomUUID()).displayName("Fixture business")
                .status(status).createdBy(person.getId()).createdAt(now).updatedAt(now).build());
        members.saveAndFlush(OrganisationMember.builder().id(UUID.randomUUID()).organisationId(org.getId()).userId(person.getId())
                .role(OrganisationRole.OWNER).status(membership).createdAt(now).updatedAt(now).build());return org;
    }
    void business(Organisation org,ApplicationStatus status) {businesses.saveAndFlush(BusinessEntitlement.builder().businessType(BusinessType.RESTAURANT)
            .businessId(UUID.randomUUID()).organisationId(org.getId()).applicationStatus(status).applicationVersion(1).updatedAt(now).build());}
    void rider(AppUser person,ApplicationStatus status) {delivery.saveAndFlush(DeliveryEntitlement.builder().userId(person.getId())
            .applicationStatus(status).applicationVersion(1).updatedAt(now).build());}
}
