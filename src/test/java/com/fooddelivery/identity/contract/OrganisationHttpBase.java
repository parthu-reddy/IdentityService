package com.fooddelivery.identity.contract;

import com.fooddelivery.common.enums.*;
import com.fooddelivery.identity.organisation.controller.InternalOrganisationController;
import com.fooddelivery.identity.organisation.entity.*;
import com.fooddelivery.identity.organisation.repository.*;
import com.fooddelivery.identity.organisation.service.LocalOrganisationAccessPolicy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import java.util.*;
import static org.mockito.Mockito.*;

/** Real local membership lookup; service-signature and gateway boundaries have separate tests. */
public abstract class OrganisationHttpBase {
    @BeforeEach public void setup(){
        UUID org=UUID.fromString("22222222-2222-2222-2222-222222222222"),user=UUID.fromString("11111111-1111-1111-1111-111111111111");
        var organisations=mock(OrganisationRepository.class);var members=mock(OrganisationMemberRepository.class);
        when(organisations.findById(org)).thenReturn(Optional.of(Organisation.builder().id(org).status(OrganisationStatus.ACTIVE).build()));
        when(members.findByOrganisationIdAndUserId(org,user)).thenReturn(Optional.of(OrganisationMember.builder().organisationId(org).userId(user).role(OrganisationRole.MANAGER).status(MembershipStatus.ACTIVE).build()));
        var policy=new LocalOrganisationAccessPolicy(members,organisations,new SimpleMeterRegistry());
        com.fooddelivery.common.contract.PlatformJson.standaloneSetup(new InternalOrganisationController(policy));
    }
}
