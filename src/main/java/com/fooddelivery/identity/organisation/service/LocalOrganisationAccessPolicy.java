package com.fooddelivery.identity.organisation.service;

import com.fooddelivery.common.security.organisation.OrganisationAccessPolicy;
import com.fooddelivery.common.dto.organisation.MembershipDto;
import com.fooddelivery.common.enums.*;
import com.fooddelivery.identity.organisation.repository.*;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import java.util.stream.Collectors;

@Component("organisationAccessPolicy") @Primary @lombok.RequiredArgsConstructor @Transactional(readOnly=true)
public class LocalOrganisationAccessPolicy implements OrganisationAccessPolicy {
    private final OrganisationMemberRepository members;
    private final OrganisationRepository organisations;
    private final MeterRegistry metrics;
    @Override public boolean can(Authentication auth,UUID org,OrganisationPermission permission) {
        boolean allowed=false;
        if (auth!=null && auth.isAuthenticated() && org!=null && permission!=null) {
            if (has(auth,"ROLE_SERVICE")) { allowed=true; }
            else if (has(auth,"ROLE_ADMIN")) { allowed=permission.readOnly(); }
            else { allowed=roleOf(auth,org).map(role -> role.grants(permission))
                .orElse(false) && organisations.findById(org).map(o -> o.getStatus()==OrganisationStatus.ACTIVE ||
                    (o.getStatus()==OrganisationStatus.SUSPENDED && permission==OrganisationPermission.ORG_VIEW)).orElse(false); }
        }
        metrics.counter("organisation.access.decisions","permission",permission==null?"INVALID":permission.name(),"result",allowed?"allow":"deny").increment();
        return allowed;
    }
    @Override public boolean canUser(UUID user,UUID org,OrganisationPermission permission) {
        if(user==null || org==null || permission==null) { return false; }
        var status=organisations.findById(org).map(o -> o.getStatus()).orElse(null);
        return (status==OrganisationStatus.ACTIVE || (status==OrganisationStatus.SUSPENDED && permission==OrganisationPermission.ORG_VIEW))
                && members.findByOrganisationIdAndUserId(org,user)
                    .filter(m -> m.getStatus()==MembershipStatus.ACTIVE && m.getRole()!=null && m.getRole().grants(permission)).isPresent();
    }
    @Override public Optional<OrganisationRole> roleOf(Authentication auth,UUID org) {
        UUID user=person(auth);if(user==null||org==null){return Optional.empty();}
        return members.findByOrganisationIdAndUserId(org,user).filter(m -> m.getStatus()==MembershipStatus.ACTIVE)
            .filter(m -> organisations.findById(org).map(o -> o.getStatus()!=OrganisationStatus.CLOSED).orElse(false)).map(m -> m.getRole());
    }
    @Override public List<UUID> organisationsOf(Authentication auth,OrganisationPermission permission) {
        UUID user=person(auth);if(user==null||permission==null || (has(auth,"ROLE_ADMIN") && !permission.readOnly())){return List.of();}
        var memberRows=members.findAllByUserIdAndStatus(user,MembershipStatus.ACTIVE);
        var orgs=organisations.findAllById(memberRows.stream().map(m -> m.getOrganisationId()).toList()).stream()
            .collect(Collectors.toMap(o -> o.getId(),o -> o.getStatus()));
        return memberRows.stream().filter(m -> m.getRole().grants(permission))
            .filter(m -> orgs.get(m.getOrganisationId())==OrganisationStatus.ACTIVE ||
                (orgs.get(m.getOrganisationId())==OrganisationStatus.SUSPENDED && permission==OrganisationPermission.ORG_VIEW))
            .map(m -> m.getOrganisationId()).toList();
    }
    public MembershipDto membership(UUID orgId,UUID userId) {
        var org=organisations.findById(orgId).orElseThrow(() -> OrganisationService.refusal(HttpStatus.NOT_FOUND,"Membership not found",orgId,userId));
        var m=members.findByOrganisationIdAndUserId(orgId,userId).filter(row -> row.getStatus()==MembershipStatus.ACTIVE)
            .orElseThrow(() -> OrganisationService.refusal(HttpStatus.NOT_FOUND,"Membership not found",orgId,userId));
        return new MembershipDto(orgId,org.getStatus(),userId,m.getRole(),m.getStatus());
    }
    public List<MembershipDto> userMemberships(UUID userId) {
        var memberRows=members.findAllByUserIdAndStatus(userId,MembershipStatus.ACTIVE);
        var orgs=organisations.findAllById(memberRows.stream().map(m -> m.getOrganisationId()).toList()).stream().collect(Collectors.toMap(o -> o.getId(),o -> o.getStatus()));
        return memberRows.stream().filter(m -> orgs.containsKey(m.getOrganisationId())).map(m -> new MembershipDto(m.getOrganisationId(),orgs.get(m.getOrganisationId()),userId,m.getRole(),m.getStatus())).toList();
    }
    public List<UUID> memberIds(UUID orgId,OrganisationPermission permission) {
        var status=organisations.findById(orgId).map(o -> o.getStatus()).orElse(null);
        if(status!=OrganisationStatus.ACTIVE && !(status==OrganisationStatus.SUSPENDED&&permission==OrganisationPermission.ORG_VIEW)){return List.of();}
        return members.findAllByOrganisationIdAndStatus(orgId,MembershipStatus.ACTIVE).stream().filter(m -> m.getRole().grants(permission)).map(m -> m.getUserId()).toList();
    }
    private static boolean has(Authentication auth,String role){return auth.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));}
    private static UUID person(Authentication auth){
        if(auth==null||!auth.isAuthenticated()){return null;}
        try{return UUID.fromString(auth.getName());}catch(IllegalArgumentException ex){return null;}
    }
}
