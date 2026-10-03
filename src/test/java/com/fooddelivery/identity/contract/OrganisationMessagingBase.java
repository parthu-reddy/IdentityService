package com.fooddelivery.identity.contract;

import com.fooddelivery.common.audit.AuditTrail;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.common.outbox.service.OutboxProcessor;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.organisation.entity.*;
import com.fooddelivery.identity.organisation.repository.*;
import com.fooddelivery.identity.organisation.service.*;
import com.fooddelivery.identity.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real OrganisationService -> serialized outbox row -> real OutboxProcessor -> embedded Kafka. */
@SpringBootTest(classes=OrganisationMessagingBase.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
    properties={"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"})
@ActiveProfiles("contract-test") @AutoConfigureMessageVerifier
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
@EmbeddedKafka(adminTimeout=60,partitions=1,topics="organisation-events")
public abstract class OrganisationMessagingBase {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("spring.kafka.bootstrap-servers",()->System.getProperty("spring.embedded.kafka.brokers","localhost:9092"));}
    @org.springframework.boot.SpringBootConfiguration @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class Config {@Bean KafkaMessageVerifier kafkaMessageVerifier(){return new KafkaMessageVerifier();}}
    @Autowired KafkaTemplate<String,String> kafka;
    @Autowired ObjectMapper mapper;
    public void fireOrganisationCreated(){publish(EventType.ORGANISATION_CREATED);}
    public void fireOrganisationMembershipChanged(){publish(EventType.ORGANISATION_MEMBERSHIP_CHANGED);}
    private void publish(EventType expected){
        UUID actor=UUID.fromString("11111111-1111-1111-1111-111111111111");
        var orgs=mock(OrganisationRepository.class);var members=mock(OrganisationMemberRepository.class);
        var invitations=mock(OrganisationInvitationRepository.class);var users=mock(UserRepository.class);
        var outbox=mock(OutboxEventRepository.class);List<OutboxEventEntity> emitted=new ArrayList<>();
        when(users.findById(actor)).thenReturn(Optional.of(AppUser.builder().id(actor).isActive(true).build()));
        when(orgs.saveAndFlush(any())).thenAnswer(i -> {Organisation o=i.getArgument(0);o.setVersion(0L);return o;});
        when(members.saveAndFlush(any())).thenAnswer(i -> {OrganisationMember m=i.getArgument(0);m.setVersion(0L);return m;});
        when(outbox.save(any())).thenAnswer(i -> {OutboxEventEntity e=i.getArgument(0);emitted.add(e);return e;});
        var service=new OrganisationService(orgs,members,invitations,users,outbox,mapper,mock(AuditTrail.class),mock(OrganisationRateLimits.class),Clock.fixed(Instant.parse("2026-10-03T10:00:00Z"),ZoneOffset.UTC),new SimpleMeterRegistry());
        service.create(new UsernamePasswordAuthenticationToken(actor.toString(),null,List.of()),"Contract organisation");
        var selected=emitted.stream().filter(e -> e.getEventType()==expected).toList();assertEquals(1,selected.size());
        when(outbox.findTop100ByStatusInOrderByCreatedAtAsc(anyList())).thenReturn(selected);
        new OutboxProcessor(outbox,kafka,new SimpleMeterRegistry()).processOutboxEvents();
        assertEquals(com.fooddelivery.common.enums.OutboxStatus.PROCESSED,selected.get(0).getStatus(),"Producer must publish through the configured aggregate topic");
    }
}
