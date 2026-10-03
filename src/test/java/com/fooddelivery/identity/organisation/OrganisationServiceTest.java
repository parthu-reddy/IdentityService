package com.fooddelivery.identity.organisation;

import com.fooddelivery.identity.organisation.service.*;
import com.fooddelivery.identity.organisation.entity.*;
import com.fooddelivery.identity.organisation.repository.*;
import com.fooddelivery.identity.repository.UserRepository;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.common.enums.*;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.audit.AuditTrail;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrganisationServiceTest {
    UUID orgId=UUID.randomUUID(),owner=UUID.randomUUID(),other=UUID.randomUUID();
    Instant now=Instant.parse("2026-10-03T10:00:00Z");
    OrganisationRepository orgs;OrganisationMemberRepository members;OrganisationInvitationRepository invitations;
    UserRepository users;OutboxEventRepository outbox;AuditTrail audit;OrganisationRateLimits limits;
    OrganisationService service;OrganisationInvitationService invites;Organisation org;
    Authentication auth(UUID id){return new UsernamePasswordAuthenticationToken(id.toString(),null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")));}
    OrganisationMember member(UUID id,OrganisationRole role){return OrganisationMember.builder().id(UUID.randomUUID()).organisationId(orgId).userId(id).role(role).status(MembershipStatus.ACTIVE).createdAt(now).updatedAt(now).version(1L).build();}
    @BeforeEach void setup(){
        orgs=mock(OrganisationRepository.class);members=mock(OrganisationMemberRepository.class);invitations=mock(OrganisationInvitationRepository.class);
        users=mock(UserRepository.class);outbox=mock(OutboxEventRepository.class);audit=mock(AuditTrail.class);limits=mock(OrganisationRateLimits.class);
        Clock clock=Clock.fixed(now,ZoneOffset.UTC);
        service=new OrganisationService(orgs,members,invitations,users,outbox,new ObjectMapper().findAndRegisterModules(),audit,limits,clock,new SimpleMeterRegistry());
        invites=new OrganisationInvitationService(service,invitations,orgs,members,users,limits,clock);
        org=Organisation.builder().id(orgId).displayName("Brand").status(OrganisationStatus.ACTIVE).createdBy(owner).createdAt(now).updatedAt(now).version(1L).build();
        when(orgs.findById(orgId)).thenReturn(Optional.of(org));when(orgs.lockById(orgId)).thenReturn(Optional.of(org));
        when(members.existsByOrganisationIdAndUserIdAndStatus(eq(orgId),any(),eq(MembershipStatus.ACTIVE))).thenReturn(true);
        when(members.findByOrganisationIdAndUserId(orgId,owner)).thenReturn(Optional.of(member(owner,OrganisationRole.OWNER)));
        when(members.findByOrganisationIdAndUserId(orgId,other)).thenReturn(Optional.of(member(other,OrganisationRole.MANAGER)));
        when(members.saveAndFlush(any())).thenAnswer(i -> {OrganisationMember m=i.getArgument(0);m.setVersion(m.getVersion()==null?0:m.getVersion()+1);return m;});
        when(orgs.saveAndFlush(any())).thenAnswer(i -> {Organisation o=i.getArgument(0);o.setVersion(o.getVersion()==null?0:o.getVersion()+1);return o;});
        when(invitations.save(any())).thenAnswer(i -> i.getArgument(0));
        when(users.findById(owner)).thenReturn(Optional.of(AppUser.builder().id(owner).phoneNumber("8999000001").isActive(true).build()));
        when(users.findById(other)).thenReturn(Optional.of(AppUser.builder().id(other).phoneNumber("8999000002").isActive(true).build()));
    }
    void refuses(int status,Runnable work){var ex=assertThrows(ResponseStatusException.class,work::run);assertEquals(status,ex.getStatusCode().value());}
    @Test void createTrimsNameAndWritesBothEventsAndAudit(){
        var result=service.create(auth(owner),"  Acme  ");assertEquals("Acme",result.displayName());assertEquals(OrganisationRole.OWNER,result.myRole());
        var captor=ArgumentCaptor.forClass(OutboxEventEntity.class);verify(outbox,times(2)).save(captor.capture());
        assertEquals(Set.of(EventType.ORGANISATION_CREATED,EventType.ORGANISATION_MEMBERSHIP_CHANGED),captor.getAllValues().stream().map(OutboxEventEntity::getEventType).collect(java.util.stream.Collectors.toSet()));
        assertTrue(captor.getAllValues().stream().allMatch(e -> e.getIdempotencyKey()!=null));verify(limits).create(owner);verify(audit).record(any(),eq(owner),eq("USER"),eq(AuditAction.ORGANISATION_CREATED),eq("ORGANISATION"),any(),any(),isNull(),eq(Map.of()));
    }
    @Test void nonmemberCannotReadAndBadNameCannotCreate(){when(members.findByOrganisationIdAndUserId(orgId,other)).thenReturn(Optional.empty());refuses(404,()->service.get(auth(other),orgId));refuses(400,()->service.create(auth(owner),"   "));}
    @Test void adminCannotPromoteToTheirOwnRankOrManagePeer(){
        when(members.findByOrganisationIdAndUserId(orgId,owner)).thenReturn(Optional.of(member(owner,OrganisationRole.ADMIN)));
        refuses(403,()->service.changeRole(auth(owner),orgId,other,OrganisationRole.ADMIN));
        when(members.findByOrganisationIdAndUserId(orgId,other)).thenReturn(Optional.of(member(other,OrganisationRole.ADMIN)));
        refuses(403,()->service.changeRole(auth(owner),orgId,other,OrganisationRole.STAFF));
    }
    @Test void selfChangesAndDirectOwnerAssignmentAreRefused(){refuses(400,()->service.changeRole(auth(owner),orgId,owner,OrganisationRole.STAFF));refuses(400,()->service.changeRole(auth(owner),orgId,other,OrganisationRole.OWNER));}
    @Test void ownerCannotLeaveOrBeRemoved(){refuses(403,()->service.remove(auth(owner),orgId,owner));}
    @Test void memberCanLeaveWithoutMemberManagePermission(){var staff=member(other,OrganisationRole.STAFF);when(members.findByOrganisationIdAndUserId(orgId,other)).thenReturn(Optional.of(staff));service.remove(auth(other),orgId,other);assertEquals(MembershipStatus.REMOVED,staff.getStatus());verify(outbox).save(any());}
    @Test void transferFlushesFormerOwnerBeforePromotionAndEmitsBothChanges(){
        var result=service.transfer(auth(owner),orgId,other);assertEquals(OrganisationRole.ADMIN,result.myRole());
        var saved=ArgumentCaptor.forClass(OrganisationMember.class);verify(members,times(2)).saveAndFlush(saved.capture());
        assertEquals(owner,saved.getAllValues().get(0).getUserId());assertEquals(OrganisationRole.ADMIN,saved.getAllValues().get(0).getRole());
        assertEquals(OrganisationRole.OWNER,saved.getAllValues().get(1).getRole());verify(outbox,times(2)).save(any());
    }
    @Test void suspendedOrganisationRefusesWrites(){org.setStatus(OrganisationStatus.SUSPENDED);refuses(403,()->service.rename(auth(owner),orgId,"New"));}
    @Test void platformAdminCannotAcquireOrganisationWritesThroughOwnerMembership(){
        Authentication admin=new UsernamePasswordAuthenticationToken(owner.toString(),null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
        refuses(403,()->service.create(admin,"Admin business"));refuses(403,()->service.rename(admin,orgId,"Renamed"));refuses(403,()->service.transfer(admin,orgId,other));
        verify(outbox,never()).save(any());
    }
    @Test void suspendedOrganisationRefusesEvenSelfRemoval(){org.setStatus(OrganisationStatus.SUSPENDED);refuses(403,()->service.remove(auth(other),orgId,other));verify(outbox,never()).save(any());}
    @Test void unchangedRoleDoesNotEmitDuplicateVersionEvent(){service.changeRole(auth(owner),orgId,other,OrganisationRole.MANAGER);verify(outbox,never()).save(any());verify(audit,never()).record(any(),any(),any(),any(),any(),any(),any(),any(),any());}
    @Test void staffCannotInviteAndOwnerCannotInviteOwner(){when(members.findByOrganisationIdAndUserId(orgId,other)).thenReturn(Optional.of(member(other,OrganisationRole.STAFF)));refuses(403,()->invites.invite(auth(other),orgId,"8999000003",OrganisationRole.STAFF));refuses(400,()->invites.invite(auth(owner),orgId,"8999000003",OrganisationRole.OWNER));}
    @Test void duplicateInvitationAndMemberLimitAreConflicts(){when(invitations.existsByOrganisationIdAndPhoneNumberAndStatus(orgId,"8999000003",InvitationStatus.PENDING)).thenReturn(true);refuses(409,()->invites.invite(auth(owner),orgId,"8999000003",OrganisationRole.STAFF));when(members.countByOrganisationIdAndStatus(orgId,MembershipStatus.ACTIVE)).thenReturn(200L);refuses(409,()->invites.invite(auth(owner),orgId,"8999000004",OrganisationRole.STAFF));}
    OrganisationInvitation pending(String phone){var i=OrganisationInvitation.builder().id(UUID.randomUUID()).organisationId(orgId).phoneNumber(phone).role(OrganisationRole.STAFF).status(InvitationStatus.PENDING).invitedBy(owner).expiresAt(now.plus(Duration.ofDays(7))).createdAt(now).updatedAt(now).build();when(invitations.organisationIdFor(i.getId())).thenReturn(Optional.of(orgId));when(invitations.findById(i.getId())).thenReturn(Optional.of(i));return i;}
    @Test void wrongPhoneCannotAccept(){var i=pending("8999000003");refuses(404,()->invites.respond(auth(other),i.getId(),true));verify(members,never()).saveAndFlush(any());}
    @Test void expiredInvitationIsRefusedAndMarkedExpired(){var i=pending("8999000002");i.setExpiresAt(now);assertThrows(InvitationExpiredException.class,()->invites.respond(auth(other),i.getId(),true));assertEquals(InvitationStatus.EXPIRED,i.getStatus());verify(members,never()).saveAndFlush(any());}
    @Test void acceptReactivatesMemberAndEmitsEvent(){var i=pending("8999000002");var removed=member(other,OrganisationRole.MANAGER);removed.setStatus(MembershipStatus.REMOVED);when(members.findByOrganisationIdAndUserId(orgId,other)).thenReturn(Optional.of(removed));assertEquals(InvitationStatus.ACCEPTED,invites.respond(auth(other),i.getId(),true).status());assertEquals(OrganisationRole.STAFF,removed.getRole());assertEquals(MembershipStatus.ACTIVE,removed.getStatus());verify(outbox).save(any());verify(limits).respond(other);}
    @Test void removedInviterCannotStillGrantAccess(){var i=pending("8999000002");when(members.findByOrganisationIdAndUserId(orgId,owner)).thenReturn(Optional.empty());refuses(404,()->invites.respond(auth(other),i.getId(),true));verify(members,never()).saveAndFlush(any());}
    @Test void invitingUnknownAccountHasSameResponseShape(){var unknown=invites.invite(auth(owner),orgId,"8999000003",OrganisationRole.STAFF);assertEquals(InvitationStatus.PENDING,unknown.status());verify(limits).invite(orgId,owner);}
}
