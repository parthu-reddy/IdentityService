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
import com.fooddelivery.identity.entitlement.repository.BusinessEntitlementRepository;
import com.fooddelivery.identity.entitlement.service.EntitlementVersionPublisher;
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
    properties={"spring.kafka.admin.auto-create=true", "spring.kafka.admin.operation-timeout=60s", "spring.kafka.admin.fail-fast=true", "spring.kafka.admin.properties.request.timeout.ms=60000", "spring.kafka.admin.properties.default.api.timeout.ms=60000", "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"})
@ActiveProfiles("contract-test") @AutoConfigureMessageVerifier
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
// Topics come from KafkaAdmin (embeddedKafkaTopics) under the spring.kafka.admin deadline set above, with broker
// auto-creation off so an undeclared topic fails. @EmbeddedKafka(topics) waits a fixed 10 s: spring-kafka-test 3.2.0's
// EmbeddedKafkaContextCustomizer ignores adminTimeout (BV-T2/BV-T3, 2026-11-12).
@EmbeddedKafka(partitions = 1, brokerProperties = "auto.create.topics.enable=false")
public abstract class OrganisationMessagingBase {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("spring.kafka.bootstrap-servers",()->System.getProperty("spring.embedded.kafka.brokers","localhost:9092"));}
    @org.springframework.boot.SpringBootConfiguration @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class Config {
        /** Every topic this context listens on or publishes to; KafkaAdmin creates them before the listeners start. */
        @org.springframework.context.annotation.Bean
        org.springframework.kafka.core.KafkaAdmin.NewTopics embeddedKafkaTopics() {
            return new org.springframework.kafka.core.KafkaAdmin.NewTopics(java.util.stream.Stream.of("organisation-events")
                    .map(name -> org.springframework.kafka.config.TopicBuilder.name(name).partitions(1).replicas(1).build())
                    .toArray(org.apache.kafka.clients.admin.NewTopic[]::new));
        }
@Bean KafkaMessageVerifier kafkaMessageVerifier(){return new KafkaMessageVerifier();}}
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
        var service=new OrganisationService(orgs,members,invitations,users,outbox,mapper,mock(AuditTrail.class),mock(OrganisationRateLimits.class),Clock.fixed(Instant.parse("2026-10-03T10:00:00Z"),ZoneOffset.UTC),new SimpleMeterRegistry(),mock(BusinessEntitlementRepository.class),mock(EntitlementVersionPublisher.class));
        service.create(new UsernamePasswordAuthenticationToken(actor.toString(),null,List.of()),"Contract organisation");
        var selected=emitted.stream().filter(e -> e.getEventType()==expected).toList();assertEquals(1,selected.size());
        when(outbox.findTop100ByStatusInOrderByCreatedAtAsc(anyList())).thenReturn(selected);
        new OutboxProcessor(outbox,kafka,new SimpleMeterRegistry()).processOutboxEvents();
        assertEquals(com.fooddelivery.common.enums.OutboxStatus.PROCESSED,selected.get(0).getStatus(),"Producer must publish through the configured aggregate topic");
    }
}
