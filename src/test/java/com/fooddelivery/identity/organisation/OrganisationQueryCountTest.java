package com.fooddelivery.identity.organisation;

import com.fooddelivery.common.enums.*;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.organisation.entity.*;
import com.fooddelivery.identity.organisation.repository.*;
import com.fooddelivery.identity.organisation.service.*;
import com.fooddelivery.identity.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties={"spring.cloud.config.enabled=false","spring.flyway.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:bp_organisation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.generate_statistics=true"})
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace=org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=OrganisationJpaTestConfiguration.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class OrganisationQueryCountTest {
    @Autowired OrganisationService service;
    @Autowired OrganisationRepository orgs;
    @Autowired OrganisationMemberRepository members;
    @Autowired UserRepository users;
    @Autowired EntityManagerFactory factory;
    @Autowired PlatformTransactionManager manager;
    @MockBean OrganisationRateLimits limits;
    Instant now=Instant.parse("2026-10-03T10:00:00Z");
    UUID addUser(int n){return users.saveAndFlush(AppUser.builder().phoneNumber(String.format("8998%06d",n)).isActive(true).build()).getId();}
    Organisation addOrg(UUID owner){return orgs.saveAndFlush(Organisation.builder().id(UUID.randomUUID()).displayName("Queries").status(OrganisationStatus.ACTIVE).createdBy(owner).createdAt(now).updatedAt(now).build());}
    void addMember(UUID org,UUID user,OrganisationRole role){members.saveAndFlush(OrganisationMember.builder().id(UUID.randomUUID()).organisationId(org).userId(user).role(role).status(MembershipStatus.ACTIVE).addedBy(user).createdAt(now).updatedAt(now).build());}
    long queries(Runnable work){var stats=factory.unwrap(SessionFactory.class).getStatistics();stats.clear();work.run();return stats.getPrepareStatementCount();}
    @Test void oneAndFiftyMembersCostTheSameNumberOfStatementsAndStaffPhonesAreMasked(){
        UUID actor=addUser(1);var org=addOrg(actor);addMember(org.getId(),actor,OrganisationRole.STAFF);
        var auth=new UsernamePasswordAuthenticationToken(actor.toString(),null,List.of());
        long one=queries(() -> assertEquals(1,service.memberList(auth,org.getId(),0,100).getNumberOfElements()));
        new TransactionTemplate(manager).execute(s -> {for(int i=2;i<=50;i++){addMember(org.getId(),addUser(i),OrganisationRole.STAFF);}return null;});
        long fifty=queries(() -> {var rows=service.memberList(auth,org.getId(),0,100);assertEquals(50,rows.getNumberOfElements());assertTrue(rows.stream().allMatch(m -> m.phoneNumber().matches("\\*{6}\\d{4}")));});
        assertTrue(one>0);assertEquals(one,fifty,"Member list must batch user lookups");assertTrue(fifty<=4,"Membership, organisation, member page and batched users only");
    }
    @Test void oneAndFiftyOrganisationsBatchCallerRolesAndDoNotCountPages(){
        UUID actor=addUser(101);var auth=new UsernamePasswordAuthenticationToken(actor.toString(),null,List.of());
        var first=addOrg(actor);addMember(first.getId(),actor,OrganisationRole.OWNER);
        long one=queries(() -> assertEquals(1,service.list(auth,0,100).getNumberOfElements()));
        new TransactionTemplate(manager).execute(s -> {for(int i=1;i<50;i++){var org=addOrg(actor);addMember(org.getId(),actor,OrganisationRole.OWNER);}return null;});
        long fifty=queries(() -> {var rows=service.list(auth,0,100);assertEquals(50,rows.getNumberOfElements());assertTrue(rows.stream().allMatch(o -> o.myRole()==OrganisationRole.OWNER));});
        assertTrue(one>0);assertEquals(one,fifty);assertTrue(fifty<=2,"Organisation slice plus one batched role query only");
    }
}
