package com.fooddelivery.identity.organisation;

import com.fooddelivery.common.enums.*;
import com.fooddelivery.identity.organisation.entity.*;
import com.fooddelivery.identity.organisation.repository.*;
import com.fooddelivery.identity.organisation.service.LocalOrganisationAccessPolicy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalOrganisationAccessPolicyTest {
    UUID org=UUID.randomUUID(),user=UUID.randomUUID();
    OrganisationRepository organisations=mock(OrganisationRepository.class);
    OrganisationMemberRepository members=mock(OrganisationMemberRepository.class);
    LocalOrganisationAccessPolicy policy=new LocalOrganisationAccessPolicy(members,organisations,new SimpleMeterRegistry());
    Organisation organisation=Organisation.builder().id(org).status(OrganisationStatus.ACTIVE).build();
    OrganisationMember membership=OrganisationMember.builder().organisationId(org).userId(user).role(OrganisationRole.STAFF).status(MembershipStatus.ACTIVE).build();
    @BeforeEach void setup(){when(organisations.findById(org)).thenReturn(Optional.of(organisation));when(members.findByOrganisationIdAndUserId(org,user)).thenReturn(Optional.of(membership));}
    UsernamePasswordAuthenticationToken auth(String role){return new UsernamePasswordAuthenticationToken(user.toString(),null,List.of(new SimpleGrantedAuthority(role)));}
    @Test void delegatesMatrixAndImmediatelySeesRoleRemovalAndSuspension(){
        for(var role:OrganisationRole.values()){membership.setRole(role);for(var permission:OrganisationPermission.values()){assertEquals(role.grants(permission),policy.can(auth("ROLE_CUSTOMER"),org,permission));}}
        organisation.setStatus(OrganisationStatus.SUSPENDED);for(var p:OrganisationPermission.values()){assertEquals(p==OrganisationPermission.ORG_VIEW,policy.can(auth("ROLE_CUSTOMER"),org,p));}
        membership.setStatus(MembershipStatus.REMOVED);assertFalse(policy.can(auth("ROLE_CUSTOMER"),org,OrganisationPermission.ORG_VIEW));
        assertTrue(policy.roleOf(auth("ROLE_CUSTOMER"),org).isEmpty());
    }
    @Test void serviceAndPlatformAdminRulesDoNotRequireMembership(){
        for(var p:OrganisationPermission.values()){assertTrue(policy.can(auth("ROLE_SERVICE"),org,p));assertEquals(p.readOnly(),policy.can(auth("ROLE_ADMIN"),org,p));}
        verifyNoInteractions(members,organisations);
    }
    @Test void internalChecksUseTheNamedUserAndImmediatelySeeRevocation(){
        assertTrue(policy.canUser(user,org,OrganisationPermission.ORDERS_OPERATE));
        assertFalse(policy.canUser(user,org,OrganisationPermission.EARNINGS_VIEW));
        assertFalse(policy.canUser(UUID.randomUUID(),org,OrganisationPermission.ORDERS_OPERATE));
        membership.setStatus(MembershipStatus.REMOVED);
        assertFalse(policy.canUser(user,org,OrganisationPermission.ORDERS_OPERATE));
        assertFalse(policy.canUser(null,org,OrganisationPermission.ORG_VIEW));
    }
    @Test void internalMembershipIsNotFoundAfterRemoval(){membership.setStatus(MembershipStatus.REMOVED);var ex=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->policy.membership(org,user));assertEquals(404,ex.getStatusCode().value());}
}
