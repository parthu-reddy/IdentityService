package com.fooddelivery.identity.organisation.service;

import com.fooddelivery.identity.organisation.entity.*;
import com.fooddelivery.identity.organisation.repository.*;
import com.fooddelivery.identity.organisation.dto.OrganisationViews.*;
import com.fooddelivery.identity.repository.UserRepository;
import com.fooddelivery.common.enums.*;
import com.fooddelivery.common.constants.*;
import com.fooddelivery.common.event.organisation.*;
import com.fooddelivery.common.audit.AuditTrail;
import com.fooddelivery.common.outbox.entity.OutboxEventEntity;
import com.fooddelivery.common.outbox.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service @lombok.RequiredArgsConstructor @lombok.extern.slf4j.Slf4j
@Transactional(readOnly = true)
public class OrganisationService {
    private final OrganisationRepository organisations;
    private final OrganisationMemberRepository members;
    private final OrganisationInvitationRepository invitations;
    private final UserRepository users;
    private final OutboxEventRepository outbox;
    private final ObjectMapper mapper;
    private final AuditTrail auditTrail;
    private final OrganisationRateLimits rateLimits;
    private final Clock clock;
    private final MeterRegistry metrics;
    @Value("${identity.organisations.max-active-members:200}") private int maxActiveMembers = 200;

    public static UUID userId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue"); }
        try { return UUID.fromString(auth.getName()); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A person session is required"); }
    }

    @Transactional
    public OrganisationView create(Authentication auth, String name) {
        UUID actor = userId(auth);
        users.findById(actor).filter(u -> u.isActive()).orElseThrow(() -> refusal(HttpStatus.UNAUTHORIZED, "User unavailable", null, actor));
        requireEverydaySession(auth);
        String trimmed = validName(name);
        rateLimits.create(actor);
        Instant now = clock.instant();
        var org = organisations.saveAndFlush(Organisation.builder().id(UUID.randomUUID()).displayName(trimmed)
            .status(OrganisationStatus.ACTIVE).createdBy(actor).createdAt(now).updatedAt(now).build());
        var member = members.saveAndFlush(OrganisationMember.builder().id(UUID.randomUUID()).organisationId(org.getId()).userId(actor)
            .role(OrganisationRole.OWNER).status(MembershipStatus.ACTIVE).addedBy(actor).createdAt(now).updatedAt(now).build());
        publish(org.getId(), EventType.ORGANISATION_CREATED, "org-created:" + org.getId(),
            new OrganisationCreatedEvent(org.getId(), org.getDisplayName(), actor, now));
        membershipChanged(member, actor);
        audit(actor, "USER", AuditAction.ORGANISATION_CREATED, "ORGANISATION", org.getId(), org.getId(), null, Map.of());
        return view(org, member.getRole());
    }

    public OrganisationView get(Authentication auth, UUID id) {
        UUID actor = userId(auth);
        var member = memberFor(id, actor);
        var org=organisation(id); requirePermission(org,member,OrganisationPermission.ORG_VIEW);
        return view(org, member.getRole());
    }

    public Slice<OrganisationView> list(Authentication auth, int page, int size) {
        UUID actor = userId(auth);
        var found = organisations.findForUser(actor, MembershipStatus.ACTIVE, page(page,size));
        if (found.isEmpty()) { return found.map(o -> view(o,null)); }
        var roles = members.findAllByOrganisationIdInAndUserIdAndStatus(found.getContent().stream().map(Organisation::getId).toList(), actor, MembershipStatus.ACTIVE)
            .stream().collect(Collectors.toMap(OrganisationMember::getOrganisationId, OrganisationMember::getRole));
        return found.map(o -> view(o,roles.get(o.getId())));
    }

    public Slice<MemberView> memberList(Authentication auth, UUID orgId, int page, int size) {
        var caller = memberFor(orgId, userId(auth));
        requirePermission(organisation(orgId),caller,OrganisationPermission.ORG_VIEW);
        return memberViews(orgId,page,size,caller.getRole().grants(OrganisationPermission.MEMBERS_MANAGE));
    }

    public Slice<MemberView> adminMembers(UUID orgId,int page,int size) { organisation(orgId); return memberViews(orgId,page,size,true); }

    private Slice<MemberView> memberViews(UUID orgId,int page,int size,boolean fullPhone) {
        var found = members.findAllByOrganisationIdAndStatus(orgId, MembershipStatus.ACTIVE, page(page,size));
        var userRows = users.findAllById(found.getContent().stream().map(OrganisationMember::getUserId).toList()).stream()
            .collect(Collectors.toMap(com.fooddelivery.identity.entity.AppUser::getId, u -> u));
        return found.map(m -> {
            var user = userRows.get(m.getUserId());
            String phone = user == null ? null : user.getPhoneNumber();
            if (!fullPhone && phone != null) { phone = "******" + phone.substring(Math.max(0,phone.length()-4)); }
            return new MemberView(m.getUserId(), user == null ? null : user.getName(), phone, m.getRole(), m.getStatus(), m.getCreatedAt());
        });
    }

    @Transactional
    public OrganisationView rename(Authentication auth, UUID orgId, String name) {
        UUID actor = userId(auth); var org = writableOrganisation(orgId, auth, OrganisationPermission.ORG_MANAGE);
        org.setDisplayName(validName(name)); org.setUpdatedAt(clock.instant());
        audit(actor,"USER",AuditAction.ORGANISATION_RENAMED,"ORGANISATION",orgId,orgId,null,Map.of());
        return view(org,OrganisationRole.OWNER);
    }

    @Transactional
    public MemberView changeRole(Authentication auth, UUID orgId, UUID targetId, OrganisationRole role) {
        UUID actor = userId(auth); writableOrganisation(orgId, auth, OrganisationPermission.MEMBERS_MANAGE);
        var caller = memberFor(orgId,actor); var target = memberFor(orgId,targetId);
        if (actor.equals(targetId)) { throw refusal(HttpStatus.BAD_REQUEST,"You cannot change your own role",orgId,actor); }
        if (role == null || role == OrganisationRole.OWNER) { throw refusal(HttpStatus.BAD_REQUEST,"Use ownership transfer to assign OWNER",orgId,actor); }
        requireLower(caller,target.getRole()); requireLower(caller,role);
        if (role == target.getRole()) { return new MemberView(targetId,null,null,role,MembershipStatus.ACTIVE,target.getCreatedAt()); }
        target.setRole(role); target.setUpdatedAt(clock.instant()); members.saveAndFlush(target); membershipChanged(target,actor);
        audit(actor,"USER",AuditAction.MEMBER_ROLE_CHANGED,"MEMBER",targetId,orgId,null,Map.of());
        return new MemberView(targetId,null,null,role,MembershipStatus.ACTIVE,target.getCreatedAt());
    }

    @Transactional
    public void remove(Authentication auth, UUID orgId, UUID targetId) {
        UUID actor = userId(auth); var org = lockVisibleOrganisation(orgId,actor);
        var caller = memberFor(orgId,actor); var target = memberFor(orgId,targetId);
        requireEverydaySession(auth);
        if (org.getStatus()!=OrganisationStatus.ACTIVE) { throw refusal(HttpStatus.FORBIDDEN,"Organisation is not active",orgId,actor); }
        if (target.getRole() == OrganisationRole.OWNER) { throw refusal(HttpStatus.FORBIDDEN,"Transfer ownership before leaving or removing the owner",orgId,actor); }
        if (!actor.equals(targetId)) {
            requirePermission(organisation(orgId),caller,OrganisationPermission.MEMBERS_MANAGE); requireLower(caller,target.getRole());
        }
        target.setStatus(MembershipStatus.REMOVED); target.setUpdatedAt(clock.instant()); members.saveAndFlush(target); membershipChanged(target,actor);
        audit(actor,"USER",AuditAction.MEMBER_REMOVED,"MEMBER",targetId,orgId,null,Map.of());
    }

    @Transactional
    public OrganisationView transfer(Authentication auth, UUID orgId, UUID targetId) {
        UUID actor = userId(auth); var org = writableOrganisation(orgId, auth, OrganisationPermission.ORG_MANAGE);
        var caller = memberFor(orgId,actor); var target = memberFor(orgId,targetId);
        if (actor.equals(targetId)) { throw refusal(HttpStatus.BAD_REQUEST,"Choose another active member",orgId,actor); }
        // Flush the demotion before promotion: PostgreSQL's partial unique index is immediate.
        caller.setRole(OrganisationRole.ADMIN); caller.setUpdatedAt(clock.instant()); members.saveAndFlush(caller);
        target.setRole(OrganisationRole.OWNER); target.setUpdatedAt(clock.instant()); members.saveAndFlush(target);
        membershipChanged(caller,actor); membershipChanged(target,actor);
        audit(actor,"USER",AuditAction.OWNERSHIP_TRANSFERRED,"ORGANISATION",orgId,orgId,null,Map.of("newOwnerId",targetId));
        return view(org,OrganisationRole.ADMIN);
    }

    public Slice<OrganisationView> adminList(int page, int size) {
        return organisations.findAllBy(page(page,size)).map(o -> view(o,null));
    }

    @Transactional
    public OrganisationView setStatus(Authentication auth, UUID id, OrganisationStatus status, String reason) {
        UUID actor=userId(auth);
        if (auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) { throw refusal(HttpStatus.FORBIDDEN,"Administrator access required",id,actor); }
        if (reason == null || reason.trim().length()<10 || reason.length()>500) { throw refusal(HttpStatus.BAD_REQUEST,"A reason of 10 to 500 characters is required",id,actor); }
        var org=organisations.lockById(id).orElseThrow(() -> refusal(HttpStatus.NOT_FOUND,"Organisation not found",id,actor));
        boolean suspending=status==OrganisationStatus.SUSPENDED && org.getStatus()==OrganisationStatus.ACTIVE;
        boolean reinstating=status==OrganisationStatus.ACTIVE && org.getStatus()==OrganisationStatus.SUSPENDED;
        if (!suspending && !reinstating) { throw refusal(HttpStatus.CONFLICT,"Organisation cannot make this transition",id,actor); }
        org.setStatus(status);org.setUpdatedAt(clock.instant());organisations.saveAndFlush(org);
        publish(id,EventType.ORGANISATION_STATUS_CHANGED,"org-status:"+id+":"+org.getVersion(),new OrganisationStatusChangedEvent(id,status,reason.trim(),clock.instant()));
        audit(actor,"ADMIN",suspending?AuditAction.ORGANISATION_SUSPENDED:AuditAction.ORGANISATION_REINSTATED,"ORGANISATION",id,id,reason.trim(),Map.of());
        return view(org,null);
    }

    public OrganisationView adminGet(UUID id) { return view(organisation(id),null); }

    Organisation organisation(UUID id) { return organisations.findById(id).orElseThrow(() -> refusal(HttpStatus.NOT_FOUND,"Organisation not found",id,null)); }
    OrganisationMember memberFor(UUID orgId, UUID actor) {
        return members.findByOrganisationIdAndUserId(orgId,actor).filter(m -> m.getStatus()==MembershipStatus.ACTIVE)
            .orElseThrow(() -> refusal(HttpStatus.NOT_FOUND,"Organisation not found",orgId,actor));
    }
    Organisation lockVisibleOrganisation(UUID id, UUID actor) {
        if (!members.existsByOrganisationIdAndUserIdAndStatus(id,actor,MembershipStatus.ACTIVE)) {
            throw refusal(HttpStatus.NOT_FOUND,"Organisation not found",id,actor);
        }
        var org=organisations.lockById(id).orElseThrow(() -> refusal(HttpStatus.NOT_FOUND,"Organisation not found",id,actor));
        // Membership can change while waiting for the organisation lock; read current state again.
        return org;
    }
    Organisation writableOrganisation(UUID id, Authentication auth, OrganisationPermission permission) {
        UUID actor=userId(auth);
        var org=lockVisibleOrganisation(id,actor); requirePermission(org,memberFor(id,actor),permission);
        requireEverydaySession(auth); return org;
    }
    static void requireEverydaySession(Authentication auth) {
        if (auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw refusal(HttpStatus.FORBIDDEN,"Use your everyday session for business changes",null,userId(auth));
        }
    }
    void requirePermission(Organisation org, OrganisationMember member, OrganisationPermission permission) {
        if (org.getStatus()==OrganisationStatus.CLOSED || (org.getStatus()==OrganisationStatus.SUSPENDED && permission!=OrganisationPermission.ORG_VIEW)
            || !member.getRole().grants(permission)) { throw refusal(HttpStatus.FORBIDDEN,"You do not have permission for this action",org.getId(),member.getUserId()); }
    }
    void requireLower(OrganisationMember caller, OrganisationRole role) {
        if (role==OrganisationRole.OWNER || (caller.getRole()!=OrganisationRole.OWNER && role.rank()>=caller.getRole().rank())) {
            throw refusal(HttpStatus.FORBIDDEN,"You can manage only roles below your own",caller.getOrganisationId(),caller.getUserId());
        }
    }
    void membershipChanged(OrganisationMember member, UUID actor) {
        publish(member.getOrganisationId(),EventType.ORGANISATION_MEMBERSHIP_CHANGED,
            "org-member:"+member.getOrganisationId()+":"+member.getUserId()+":"+member.getVersion(),
            new OrganisationMembershipChangedEvent(member.getOrganisationId(),member.getUserId(),
                member.getStatus()==MembershipStatus.REMOVED?null:member.getRole(),member.getStatus(),actor,clock.instant()));
    }
    void publish(UUID org, EventType type, String key, Object event) {
        try {
            outbox.save(OutboxEventEntity.builder().id(UUID.randomUUID()).aggregateType(AggregateType.ORGANISATION)
                .aggregateId(org.toString()).eventType(type).payload(mapper.writeValueAsString(event))
                .idempotencyKey(key).createdAt(clock.instant()).status(OutboxStatus.UNPROCESSED).build());
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize organisation event",ex); }
    }
    void audit(UUID actor,String kind,AuditAction action,String subject,UUID subjectId,UUID org,String reason,Map<String,Object> details) {
        auditTrail.record(clock.instant(),actor,kind,action,subject,subjectId,org,reason,details);
    }
    void requireCapacity(UUID orgId, boolean includeInvitations) {
        long count=members.countByOrganisationIdAndStatus(orgId,MembershipStatus.ACTIVE);
        if (includeInvitations) { count+=invitations.findAllByOrganisationIdAndStatus(orgId,InvitationStatus.PENDING).stream().filter(i -> i.getExpiresAt().isAfter(clock.instant())).count(); }
        if (count>=maxActiveMembers) { throw refusal(HttpStatus.CONFLICT,"The organisation member limit has been reached",orgId,null); }
    }
    void invitationMetric(String result) { metrics.counter("organisation.invitations","result",result).increment(); }
    static Pageable page(int page,int size) {
        if (page<0 || size<1 || size>100) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Page must be non-negative and size between 1 and 100"); }
        return PageRequest.of(page,size,Sort.by(Sort.Order.asc("createdAt"),Sort.Order.asc("id")));
    }
    private static String validName(String name) {
        if (name==null || name.trim().isEmpty() || name.trim().length()>120) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Name must contain 1 to 120 characters"); }
        return name.trim();
    }
    static OrganisationView view(Organisation org,OrganisationRole role) { return new OrganisationView(org.getId(),org.getDisplayName(),org.getStatus(),role,org.getCreatedAt(),org.getUpdatedAt()); }
    static ResponseStatusException refusal(HttpStatus status,String reason,UUID org,UUID user) {
        log.warn("Organisation request refused status={} organisationId={} userId={}",status.value(),org,user);
        return new ResponseStatusException(status,reason);
    }
}
