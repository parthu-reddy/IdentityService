package com.fooddelivery.identity.organisation.service;

import com.fooddelivery.identity.organisation.entity.*;
import com.fooddelivery.identity.organisation.repository.*;
import com.fooddelivery.identity.organisation.dto.OrganisationViews.*;
import com.fooddelivery.identity.repository.UserRepository;
import com.fooddelivery.common.enums.*;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
import static com.fooddelivery.identity.organisation.service.OrganisationService.*;

@Service @lombok.RequiredArgsConstructor @Transactional(readOnly=true)
public class OrganisationInvitationService {
    private final OrganisationService organisationService;
    private final OrganisationInvitationRepository invitations;
    private final OrganisationRepository organisations;
    private final OrganisationMemberRepository members;
    private final UserRepository users;
    private final OrganisationRateLimits rateLimits;
    private final Clock clock;

    @Transactional
    public InvitationView invite(Authentication auth,UUID orgId,String phone,OrganisationRole role) {
        UUID actor=userId(auth);
        organisationService.writableOrganisation(orgId,auth,OrganisationPermission.MEMBERS_MANAGE);
        var caller=organisationService.memberFor(orgId,actor);
        if (phone==null || !phone.matches("[0-9]{10}") || role==null || role==OrganisationRole.OWNER) {
            throw refusal(HttpStatus.BAD_REQUEST,"Use a ten-digit phone and an ADMIN, MANAGER or STAFF role",orgId,actor);
        }
        organisationService.requireLower(caller,role);
        // Expired pending invitations must release their unique slot before a fresh invite.
        var pending=invitations.findAllByOrganisationIdAndStatus(orgId,InvitationStatus.PENDING);
        pending.stream().filter(i -> !i.getExpiresAt().isAfter(clock.instant())).forEach(this::expire);
        invitations.flush();
        if (invitations.existsByOrganisationIdAndPhoneNumberAndStatus(orgId,phone,InvitationStatus.PENDING)) {
            throw refusal(HttpStatus.CONFLICT,"An invitation is already pending",orgId,actor);
        }
        var known=users.findByPhoneNumber(phone);
        if (known.isPresent() && members.existsByOrganisationIdAndUserIdAndStatus(orgId,known.get().getId(),MembershipStatus.ACTIVE)) {
            throw refusal(HttpStatus.CONFLICT,"This person is already a member",orgId,actor);
        }
        organisationService.requireCapacity(orgId,true); rateLimits.invite(orgId,actor);
        Instant now=clock.instant();
        var invite=invitations.save(OrganisationInvitation.builder().id(UUID.randomUUID()).organisationId(orgId)
            .phoneNumber(phone).role(role).status(InvitationStatus.PENDING).invitedBy(actor)
            .expiresAt(now.plus(Duration.ofDays(7))).createdAt(now).updatedAt(now).build());
        organisationService.audit(actor,"USER",AuditAction.MEMBER_INVITED,"INVITATION",invite.getId(),orgId,null,Map.of());
        organisationService.invitationMetric("created"); return view(invite);
    }

    public Slice<InvitationView> list(Authentication auth,UUID orgId,int page,int size) {
        var org=organisationService.organisation(orgId);
        var caller=organisationService.memberFor(orgId,userId(auth));
        organisationService.requirePermission(org,caller,OrganisationPermission.MEMBERS_MANAGE);
        return invitations.findAllByOrganisationId(orgId,page(page,size)).map(this::view);
    }

    public Slice<InvitationView> mine(Authentication auth,int page,int size) {
        UUID actor=userId(auth); var user=users.findById(actor).orElseThrow(() -> refusal(HttpStatus.UNAUTHORIZED,"User unavailable",null,actor));
        return invitations.findAllByPhoneNumberAndStatus(user.getPhoneNumber(),InvitationStatus.PENDING,page(page,size)).map(this::view);
    }

    @Transactional(noRollbackFor=InvitationExpiredException.class)
    public InvitationView respond(Authentication auth,UUID invitationId,boolean accept) {
        UUID actor=userId(auth);
        UUID orgId=invitations.organisationIdFor(invitationId).orElseThrow(() -> refusal(HttpStatus.NOT_FOUND,"Invitation not found",null,actor));
        var org=organisations.lockById(orgId).orElseThrow(() -> refusal(HttpStatus.NOT_FOUND,"Invitation not found",orgId,actor));
        var invitation=invitations.findById(invitationId).orElseThrow(() -> refusal(HttpStatus.NOT_FOUND,"Invitation not found",orgId,actor));
        var user=users.findById(actor).orElseThrow(() -> refusal(HttpStatus.UNAUTHORIZED,"User unavailable",orgId,actor));
        if (!user.getPhoneNumber().equals(invitation.getPhoneNumber())) { throw refusal(HttpStatus.NOT_FOUND,"Invitation not found",orgId,actor); }
        requireEverydaySession(auth);
        rateLimits.respond(actor);
        if (invitation.getStatus()==InvitationStatus.PENDING && !invitation.getExpiresAt().isAfter(clock.instant())) {
            expire(invitation); throw new InvitationExpiredException();
        }
        if (invitation.getStatus()!=InvitationStatus.PENDING) { throw refusal(HttpStatus.CONFLICT,"Invitation has already been answered",orgId,actor); }
        if (org.getStatus()!=OrganisationStatus.ACTIVE) { throw refusal(HttpStatus.CONFLICT,"The organisation is not active",orgId,actor); }
        // An invite never lets a demoted/removed inviter grant their former powers.
        if (accept) {
            var inviter=organisationService.memberFor(orgId,invitation.getInvitedBy());
            organisationService.requirePermission(org,inviter,OrganisationPermission.MEMBERS_MANAGE);
            organisationService.requireLower(inviter,invitation.getRole());
            organisationService.requireCapacity(orgId,false);
            var member=members.findByOrganisationIdAndUserId(orgId,actor).orElseGet(() -> OrganisationMember.builder()
                .id(UUID.randomUUID()).organisationId(orgId).userId(actor).createdAt(clock.instant()).build());
            if (member.getStatus()==MembershipStatus.ACTIVE) { throw refusal(HttpStatus.CONFLICT,"Already a member",orgId,actor); }
            member.setStatus(MembershipStatus.ACTIVE);member.setRole(invitation.getRole());member.setAddedBy(invitation.getInvitedBy());
            member.setUpdatedAt(clock.instant());members.saveAndFlush(member);organisationService.membershipChanged(member,actor);
        }
        invitation.setStatus(accept?InvitationStatus.ACCEPTED:InvitationStatus.DECLINED);
        invitation.setRespondedBy(actor);invitation.setUpdatedAt(clock.instant());
        organisationService.audit(actor,"USER",accept?AuditAction.INVITATION_ACCEPTED:AuditAction.INVITATION_DECLINED,
            "INVITATION",invitationId,orgId,null,Map.of());
        organisationService.invitationMetric(accept?"accepted":"declined"); return view(invitation);
    }

    @Transactional
    public void revoke(Authentication auth,UUID orgId,UUID invitationId) {
        UUID actor=userId(auth);organisationService.writableOrganisation(orgId,auth,OrganisationPermission.MEMBERS_MANAGE);
        var invite=invitations.findById(invitationId).filter(i -> orgId.equals(i.getOrganisationId()))
            .orElseThrow(() -> refusal(HttpStatus.NOT_FOUND,"Invitation not found",orgId,actor));
        organisationService.requireLower(organisationService.memberFor(orgId,actor),invite.getRole());
        if (invite.getStatus()!=InvitationStatus.PENDING) { throw refusal(HttpStatus.CONFLICT,"Only pending invitations can be revoked",orgId,actor); }
        invite.setStatus(InvitationStatus.REVOKED);invite.setUpdatedAt(clock.instant());
        organisationService.audit(actor,"USER",AuditAction.INVITATION_REVOKED,"INVITATION",invitationId,orgId,null,Map.of());
        organisationService.invitationMetric("revoked");
    }

    private void expire(OrganisationInvitation invitation) {
        invitation.setStatus(InvitationStatus.EXPIRED);invitation.setUpdatedAt(clock.instant());
        organisationService.audit(null,"SERVICE",AuditAction.INVITATION_EXPIRED,"INVITATION",invitation.getId(),invitation.getOrganisationId(),null,Map.of());
        organisationService.invitationMetric("expired");
    }
    private InvitationView view(OrganisationInvitation i) {
        InvitationStatus status=i.getStatus()==InvitationStatus.PENDING && !i.getExpiresAt().isAfter(clock.instant())?InvitationStatus.EXPIRED:i.getStatus();
        return new InvitationView(i.getId(),i.getOrganisationId(),i.getPhoneNumber(),i.getRole(),status,i.getExpiresAt(),i.getCreatedAt());
    }
}
