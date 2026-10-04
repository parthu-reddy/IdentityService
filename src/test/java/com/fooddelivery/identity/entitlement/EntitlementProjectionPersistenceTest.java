package com.fooddelivery.identity.entitlement;

import com.fooddelivery.common.constants.RedisKeyConstants;
import com.fooddelivery.common.enums.*;
import com.fooddelivery.common.event.application.*;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.entity.UserRole;
import com.fooddelivery.identity.entitlement.entity.BusinessEntitlement;
import com.fooddelivery.identity.entitlement.repository.*;
import com.fooddelivery.identity.entitlement.service.*;
import com.fooddelivery.identity.organisation.entity.*;
import com.fooddelivery.identity.organisation.repository.*;
import com.fooddelivery.identity.port.CachePort;
import com.fooddelivery.identity.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Embedded persistence proof; no external database, Redis, Kafka or Docker fixture. */
@DataJpaTest(properties={"spring.cloud.config.enabled=false","spring.flyway.enabled=false",
    "spring.cloud.discovery.enabled=false","eureka.client.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:bp_entitlements;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.fooddelivery.identity.entitlement.EntitlementProjectionPersistenceTest$VersionSqlInspector"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=EntitlementJpaTestConfiguration.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class EntitlementProjectionPersistenceTest {
    public static class VersionSqlInspector implements org.hibernate.resource.jdbc.spi.StatementInspector {
        static final ThreadLocal<List<String>> updates=ThreadLocal.withInitial(ArrayList::new);
        @Override public String inspect(String sql) {
            if (sql.toLowerCase(Locale.ROOT).matches("(?s).*update\\s+users(?:\\s+\\w+)?\\s+set\\s+entitlements_version.*")) updates.get().add(sql);
            return sql;
        }
    }
    @Autowired EntitlementProjectionService projection;
    @Autowired EntitlementVersionPublisher publisher;
    @Autowired EntitlementService entitlements;
    @Autowired UserRepository users;
    @Autowired UserRoleRepository staff;
    @Autowired OrganisationRepository organisations;
    @Autowired OrganisationMemberRepository members;
    @Autowired BusinessEntitlementRepository businesses;
    @Autowired DeliveryEntitlementRepository delivery;
    @Autowired EntitlementPublicationRepository pending;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;
    @MockBean CachePort cache;
    static final java.util.concurrent.atomic.AtomicInteger phones=new java.util.concurrent.atomic.AtomicInteger();
    final Instant now=Instant.parse("2026-10-03T10:00:00Z");
    record Fixture(UUID userId,UUID orgId,UUID brandId) { }
    Fixture fixture() {
        var user=users.saveAndFlush(AppUser.builder().phoneNumber("899900"+String.format("%04d",phones.incrementAndGet())).isActive(true).build());
        var org=organisations.saveAndFlush(Organisation.builder().id(UUID.randomUUID()).displayName("Owned test organisation")
                .status(OrganisationStatus.ACTIVE).createdBy(user.getId()).createdAt(now).updatedAt(now).build());
        members.saveAndFlush(OrganisationMember.builder().id(UUID.randomUUID()).organisationId(org.getId()).userId(user.getId())
                .role(OrganisationRole.OWNER).status(MembershipStatus.ACTIVE).addedBy(user.getId()).createdAt(now).updatedAt(now).build());
        return new Fixture(user.getId(),org.getId(),UUID.randomUUID());
    }
    RestaurantApplicationStatusChangedEvent restaurant(Fixture f,ApplicationStatus status,long version) {
        return new RestaurantApplicationStatusChangedEvent(f.brandId(),f.orgId(),status,null,version,now);
    }
    long version(Fixture f){return users.findById(f.userId()).orElseThrow().getEntitlementsVersion();}

    @Test void approvalCommitsProjectionVersionAndOnlyThenPublishesOneBatch() {
        var f=fixture();
        doAnswer(call -> {
            assertEquals(1,version(f));
            assertEquals(ApplicationStatus.APPROVED,businesses.findById(new BusinessEntitlement.Key(BusinessType.RESTAURANT,f.brandId())).orElseThrow().getApplicationStatus());
            return null;
        }).when(cache).putVersions(anyMap(),any(Duration.class));
        assertTrue(projection.restaurant(restaurant(f,ApplicationStatus.APPROVED,1)));
        verify(cache).putVersions(Map.of(RedisKeyConstants.ENTITLEMENTS_VERSION+f.userId(),"1"),Duration.ofDays(2));
        assertFalse(pending.existsById(f.userId()));
        assertEquals(List.of(RoleName.CUSTOMER,RoleName.RESTAURANT,RoleName.BUSINESS),entitlements.rolesFor(f.userId()));
    }
    @Test void duplicateAndOlderApplicationVersionsCannotRegressApprovalOrBumpAgain() {
        var f=fixture();projection.restaurant(restaurant(f,ApplicationStatus.APPROVED,3));
        assertFalse(projection.restaurant(restaurant(f,ApplicationStatus.REJECTED,2)));
        assertFalse(projection.restaurant(restaurant(f,ApplicationStatus.SUSPENDED,3)));
        assertEquals(1,version(f));
        verify(cache,times(1)).putVersions(anyMap(),any(Duration.class));
        assertTrue(entitlements.rolesFor(f.userId()).contains(RoleName.RESTAURANT));
    }
    @Test void rollbackKeepsProjectionCounterRetryRowAndCacheUnchanged() {
        var f=fixture();
        assertThrows(IllegalStateException.class,() -> new TransactionTemplate(manager).execute(status -> {
            projection.restaurant(restaurant(f,ApplicationStatus.APPROVED,1));
            em.flush();throw new IllegalStateException("Abort entitlement transaction");
        }));
        assertEquals(0,version(f));assertFalse(pending.existsById(f.userId()));
        assertFalse(businesses.existsById(new BusinessEntitlement.Key(BusinessType.RESTAURANT,f.brandId())));
        verify(cache,never()).putVersions(anyMap(),any(Duration.class));
    }
    @Test void publicationFailureRetainsCommittedTruthAndDurableRetryUntilSuccessful() {
        var f=fixture();doThrow(new IllegalStateException("Redis unavailable")).when(cache).putVersions(anyMap(),any(Duration.class));
        assertTrue(projection.restaurant(restaurant(f,ApplicationStatus.APPROVED,1)));
        assertEquals(1,version(f));assertEquals(1,pending.findById(f.userId()).orElseThrow().getEntitlementsVersion());
        doNothing().when(cache).putVersions(anyMap(),any(Duration.class));publisher.retryPending();
        assertFalse(pending.existsById(f.userId()));
        verify(cache,times(2)).putVersions(Map.of(RedisKeyConstants.ENTITLEMENTS_VERSION+f.userId(),"1"),Duration.ofDays(2));
    }
    @Test void ordinaryDirtyUserUpdatesCannotOverwriteTheAtomicVersionBump() {
        var f=fixture();new TransactionTemplate(manager).executeWithoutResult(status -> {
            var managed=users.findById(f.userId()).orElseThrow();
            publisher.bump(List.of(f.userId()));managed.setName("Profile changed after version bump");em.flush();
        });
        assertEquals(1,version(f));assertEquals("Profile changed after version bump",users.findById(f.userId()).orElseThrow().getName());
    }
    @Test void newerSuspensionRevokesBusinessAccessAndActiveMembershipIsRequired() {
        var f=fixture();projection.restaurant(restaurant(f,ApplicationStatus.APPROVED,1));
        projection.restaurant(restaurant(f,ApplicationStatus.SUSPENDED,2));
        assertEquals(List.of(RoleName.CUSTOMER),entitlements.rolesFor(f.userId()));assertEquals(2,version(f));
        projection.restaurant(restaurant(f,ApplicationStatus.APPROVED,3));
        var member=members.findByOrganisationIdAndUserId(f.orgId(),f.userId()).orElseThrow();
        member.setStatus(MembershipStatus.REMOVED);members.saveAndFlush(member);
        assertEquals(List.of(RoleName.CUSTOMER),entitlements.rolesFor(f.userId()));
    }
    @Test void personalDeliveryApprovalDoesNotGrantBusinessOrAdministrativeAccess() {
        var f=fixture();staff.saveAndFlush(UserRole.builder().user(users.findById(f.userId()).orElseThrow())
                .roleName(RoleName.ADMIN).build());
        projection.delivery(new DeliveryApplicationStatusChangedEvent(f.userId(),ApplicationStatus.APPROVED,null,1,now));
        assertEquals(List.of(RoleName.CUSTOMER,RoleName.DELIVERY),entitlements.rolesFor(f.userId()));
        assertTrue(entitlements.isAdministrator(f.userId()));
        projection.delivery(new DeliveryApplicationStatusChangedEvent(f.userId(),ApplicationStatus.SUSPENDED,null,2,now));
        assertEquals(List.of(RoleName.CUSTOMER),entitlements.rolesFor(f.userId()));
        var user=users.findById(f.userId()).orElseThrow();user.setActive(false);users.saveAndFlush(user);
        assertThrows(ResponseStatusException.class,() -> entitlements.rolesFor(f.userId()));
    }
    @Test void newerApplicationCannotMoveAnExistingBusinessToAnotherOrganisation() {
        var f=fixture();var other=fixture();projection.restaurant(restaurant(f,ApplicationStatus.APPROVED,1));
        assertThrows(IllegalArgumentException.class,() -> projection.restaurant(new RestaurantApplicationStatusChangedEvent(
                f.brandId(),other.orgId(),ApplicationStatus.SUSPENDED,null,2,now)));
        assertEquals(f.orgId(),businesses.findById(new BusinessEntitlement.Key(BusinessType.RESTAURANT,f.brandId())).orElseThrow().getOrganisationId());
        assertEquals(1,version(f));assertEquals(0,version(other));
    }
    @Test void fiftyAffectedPeopleUseOneSetBasedVersionUpdateAndOneCacheBatch() {
        var ids=new ArrayList<UUID>();
        for (int i=0;i<50;i++) ids.add(fixture().userId());
        VersionSqlInspector.updates.get().clear();
        new TransactionTemplate(manager).executeWithoutResult(status -> publisher.bump(ids));
        assertEquals(1,VersionSqlInspector.updates.get().size(),"Entitlement counters must use one SQL update for the entire affected set");
        var values=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(cache,times(1)).putVersions(values.capture(),eq(Duration.ofDays(2)));
        assertEquals(50,values.getValue().size());
        assertTrue(users.entitlementVersions(ids).stream().allMatch(row -> (Long)row[1]==1L));
        VersionSqlInspector.updates.remove();
    }
}
