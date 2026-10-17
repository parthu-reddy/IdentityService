package com.fooddelivery.identity.organisation;

import com.fooddelivery.common.audit.AuditReader;
import com.fooddelivery.common.enums.*;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.organisation.entity.*;
import com.fooddelivery.identity.organisation.repository.*;
import com.fooddelivery.identity.organisation.service.*;
import com.fooddelivery.identity.repository.UserRepository;
import com.fooddelivery.identity.port.CachePort;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * H2 exercises Hibernate and real transaction boundaries, including JSON audit persistence.
 * Hibernate-generated DDL does not reproduce PostgreSQL partial unique indexes or append-only
 * triggers. Those guarantees require the separate PostgreSQL fresh-schema test; this class proves
 * ownership through the real service and never claims PostgreSQL schema coverage.
 */
@DataJpaTest(properties={"spring.cloud.config.enabled=false","spring.flyway.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:bp_organisation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace=org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=OrganisationJpaTestConfiguration.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class OrganisationPersistenceTest {
    @Autowired OrganisationService service;
    @Autowired OrganisationInvitationService invitations;
    @Autowired OrganisationRepository orgs;
    @Autowired OrganisationMemberRepository members;
    @Autowired OrganisationInvitationRepository invitationRows;
    @Autowired UserRepository users;
    @Autowired OutboxEventRepository outbox;
    @Autowired AuditReader audit;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;
    @Autowired CachePort cache;
    @MockBean OrganisationRateLimits limits;
    final Instant now=Instant.parse("2026-10-03T10:00:00Z");
    Authentication auth(UUID id){return new UsernamePasswordAuthenticationToken(id.toString(),null,List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));}
    UUID user(String phone){return users.saveAndFlush(AppUser.builder().phoneNumber(phone).isActive(true).build()).getId();}
    long auditCount(){return new TransactionTemplate(manager).execute(s -> em.createQuery("select count(a) from AuditEvent a",Long.class).getSingleResult());}

    // A background EntitlementVersionPublisher.retryPending would add statements and cache calls mid-test.
    @Test void sliceRunsNoScheduledJobs(@Autowired org.springframework.context.ApplicationContext context){
        assertTrue(context.getBeansOfType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class).isEmpty(),
            "@EnableScheduling must stay off IdentityApplication; slice tests inherit its annotations");
    }
    @Test void committedActionHasOrganisationOwnerOutboxAndAuditReadFromNewTransactions(){
        UUID actor=user("8999000101");
        var created=service.create(auth(actor),"Committed organisation");
        assertTrue(orgs.findById(created.id()).isPresent());
        var member=members.findByOrganisationIdAndUserId(created.id(),actor).orElseThrow();
        assertEquals(OrganisationRole.OWNER,member.getRole());
        var events=outbox.findAll().stream().filter(e -> e.getAggregateId().equals(created.id().toString())).toList();
        assertEquals(2,events.size());
        assertEquals(2,events.stream().map(e -> e.getIdempotencyKey()).distinct().count());
        var history=audit.history("ORGANISATION",created.id(),0,20);
        assertEquals(1,history.getNumberOfElements());
        assertEquals(AuditAction.ORGANISATION_CREATED,history.getContent().get(0).getAction());
        assertEquals(Map.of(),history.getContent().get(0).getDetails());
        assertEquals(1,users.findById(actor).orElseThrow().getEntitlementsVersion());
    }
    @Test void rollbackLeavesNoOrganisationMemberOutboxOrAudit(){
        UUID actor=user("8999000102");long orgBefore=orgs.count(),memberBefore=members.count(),outboxBefore=outbox.count(),auditBefore=auditCount();
        assertThrows(IllegalStateException.class,() -> new TransactionTemplate(manager).execute(s -> {
            service.create(auth(actor),"Rolled back organisation");
            em.flush();throw new IllegalStateException("Abort business action");
        }));
        assertEquals(orgBefore,orgs.count());assertEquals(memberBefore,members.count());
        assertEquals(outboxBefore,outbox.count());assertEquals(auditBefore,auditCount());
        assertEquals(0,users.findById(actor).orElseThrow().getEntitlementsVersion());
    }
    @Test void transferCommitsExactlyOneOwnerAndAuditJsonIdentifier(){
        UUID owner=user("8999000103"),next=user("8999000104");
        var created=service.create(auth(owner),"Transfer organisation");
        var invite=invitations.invite(auth(owner),created.id(),"8999000104",OrganisationRole.ADMIN);
        invitations.respond(auth(next),invite.id(),true);
        service.transfer(auth(owner),created.id(),next);
        var rows=members.findAllByOrganisationIdAndStatus(created.id(),MembershipStatus.ACTIVE);
        assertEquals(1,rows.stream().filter(m -> m.getRole()==OrganisationRole.OWNER).count());
        assertEquals(OrganisationRole.OWNER,members.findByOrganisationIdAndUserId(created.id(),next).orElseThrow().getRole());
        assertEquals(OrganisationRole.ADMIN,members.findByOrganisationIdAndUserId(created.id(),owner).orElseThrow().getRole());
        assertEquals(2,users.findById(owner).orElseThrow().getEntitlementsVersion());
        assertEquals(2,users.findById(next).orElseThrow().getEntitlementsVersion());
        var history=audit.history("ORGANISATION",created.id(),0,20);
        var transfer=history.stream().filter(a -> a.getAction()==AuditAction.OWNERSHIP_TRANSFERRED).findFirst().orElseThrow();
        assertEquals(next.toString(),transfer.getDetails().get("newOwnerId").toString());
    }
    @Test void invitationSmsIsStoredOnceInTheInviteTransactionWithoutUserEnteredText(){
        UUID owner=user("8999000111");
        var org=service.create(auth(owner),"Visit evil.example now");
        var unknown=invitations.invite(auth(owner),org.id(),"8999000112",OrganisationRole.MANAGER);
        var rows=outbox.findAll().stream().filter(e -> ("organisation-invitation:"+unknown.id()).equals(e.getIdempotencyKey())).toList();
        assertEquals(1,rows.size());
        var payload=rows.get(0).getPayload();
        assertEquals(com.fooddelivery.common.constants.EventType.NOTIFICATION_REQUEST,rows.get(0).getEventType());
        assertTrue(payload.contains("\"explicitRecipient\":\"8999000112\""));
        assertTrue(payload.contains("ORGANISATION_INVITATION"));
        assertTrue(payload.contains("a manager"));
        assertTrue(payload.contains("\"userId\":null"));
        assertFalse(payload.contains("evil.example"),"organisation names are user-entered and never reach the SMS");

        UUID existing=user("8999000113");
        var known=invitations.invite(auth(owner),org.id(),"8999000113",OrganisationRole.STAFF);
        var knownPayload=outbox.findAll().stream().filter(e -> ("organisation-invitation:"+known.id()).equals(e.getIdempotencyKey())).findFirst().orElseThrow().getPayload();
        assertTrue(knownPayload.contains("\"userId\":\""+existing+"\""),"an existing account keeps its channel preferences");

        long before=outbox.count();
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> invitations.invite(auth(owner),org.id(),"8999000112",OrganisationRole.STAFF));
        assertEquals(before,outbox.count(),"a refused duplicate invitation sends nothing");
    }
    @Test void expiredAcceptanceCommitsExpiryButNeverAddsMembership(){
        UUID owner=user("8999000105"),invitee=user("8999000106");
        var org=service.create(auth(owner),"Expired invitation");
        var invite=invitations.invite(auth(owner),org.id(),"8999000106",OrganisationRole.STAFF);
        new TransactionTemplate(manager).execute(s -> {var row=invitationRows.findById(invite.id()).orElseThrow();row.setExpiresAt(now);return null;});
        assertThrows(InvitationExpiredException.class,() -> invitations.respond(auth(invitee),invite.id(),true));
        assertEquals(InvitationStatus.EXPIRED,invitationRows.findById(invite.id()).orElseThrow().getStatus());
        assertTrue(members.findByOrganisationIdAndUserId(org.id(),invitee).isEmpty());
    }
    @Test void removalInvalidatesTheRemovedPersonAndStatusChangesOnlyInvalidateActiveMembers(){
        UUID owner=user("8999000107"),invitee=user("8999000108");
        var org=service.create(auth(owner),"Versioned membership");
        var invite=invitations.invite(auth(owner),org.id(),"8999000108",OrganisationRole.STAFF);
        invitations.respond(auth(invitee),invite.id(),true);
        clearInvocations(cache);
        service.remove(auth(owner),org.id(),invitee);
        assertEquals(2,users.findById(invitee).orElseThrow().getEntitlementsVersion());
        verify(cache).putVersions(Map.of("ENTITLEMENTS_VERSION:"+invitee,"2"),Duration.ofDays(2));
        var admin=new UsernamePasswordAuthenticationToken(owner.toString(),null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        service.setStatus(admin,org.id(),OrganisationStatus.SUSPENDED,"Safety review required");
        assertEquals(2,users.findById(owner).orElseThrow().getEntitlementsVersion());
        assertEquals(2,users.findById(invitee).orElseThrow().getEntitlementsVersion());
        service.setStatus(admin,org.id(),OrganisationStatus.ACTIVE,"Safety review completed");
        assertEquals(3,users.findById(owner).orElseThrow().getEntitlementsVersion());
        assertEquals(2,users.findById(invitee).orElseThrow().getEntitlementsVersion());
        verify(cache).putVersions(Map.of("ENTITLEMENTS_VERSION:"+owner,"2"),Duration.ofDays(2));
        verify(cache).putVersions(Map.of("ENTITLEMENTS_VERSION:"+owner,"3"),Duration.ofDays(2));
        verifyNoMoreInteractions(cache);
    }
    @Test void rolledBackSuspensionLeavesStatusVersionAndCacheUnchanged(){
        UUID owner=user("8999000109");var org=service.create(auth(owner),"Rollback status");
        var admin=new UsernamePasswordAuthenticationToken(owner.toString(),null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        clearInvocations(cache);
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(manager).execute(s->{
            service.setStatus(admin,org.id(),OrganisationStatus.SUSPENDED,"Safety review required");
            em.flush();throw new IllegalStateException("Abort suspension");
        }));
        assertEquals(OrganisationStatus.ACTIVE,orgs.findById(org.id()).orElseThrow().getStatus());
        assertEquals(1,users.findById(owner).orElseThrow().getEntitlementsVersion());
        verifyNoInteractions(cache);
    }
}
