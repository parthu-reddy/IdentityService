package com.fooddelivery.identity.organisation;

import com.fooddelivery.identity.organisation.controller.*;
import com.fooddelivery.identity.organisation.service.*;
import com.fooddelivery.identity.organisation.repository.*;
import com.fooddelivery.identity.organisation.entity.*;
import com.fooddelivery.common.enums.*;
import com.fooddelivery.common.security.CommonSecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrganisationController.class)
@ContextConfiguration(classes={OrganisationController.class,OrganisationService.class,OrganisationInvitationService.class,
    CommonSecurityConfig.class,com.fooddelivery.common.exception.GlobalExceptionHandler.class,OrganisationExceptionHandler.class})
class OrganisationControllerAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockBean OrganisationRepository orgs;@MockBean OrganisationMemberRepository members;@MockBean OrganisationInvitationRepository invitations;
    @MockBean com.fooddelivery.identity.repository.UserRepository users;
    @MockBean com.fooddelivery.common.outbox.repository.OutboxEventRepository outbox;
    @MockBean com.fooddelivery.common.audit.AuditTrail audit;
    @MockBean OrganisationRateLimits limits;
    @MockBean Clock clock;@MockBean io.micrometer.core.instrument.MeterRegistry registry;
    @MockBean com.fooddelivery.common.security.SecurityContextFilter identityFilter;
    UUID org=UUID.randomUUID(),actor=UUID.randomUUID();
    @BeforeEach void setup() throws Exception {
        com.fooddelivery.common.test.MockIdentityFilterSupport.passThrough(identityFilter);
        when(orgs.lockById(org)).thenReturn(Optional.of(Organisation.builder().id(org).status(OrganisationStatus.ACTIVE).build()));
        when(members.existsByOrganisationIdAndUserIdAndStatus(org,actor,MembershipStatus.ACTIVE)).thenReturn(true);
    }
    void role(OrganisationRole role){when(members.findByOrganisationIdAndUserId(org,actor)).thenReturn(Optional.of(OrganisationMember.builder().organisationId(org).userId(actor).status(MembershipStatus.ACTIVE).role(role).build()));}
    @Test void nonmemberGets404() throws Exception {mvc.perform(get("/api/v1/organisations/"+org).with(user(actor.toString()).roles("CUSTOMER"))).andExpect(status().isNotFound());}
    @Test void staffCannotInvite() throws Exception {role(OrganisationRole.STAFF);mvc.perform(post("/api/v1/organisations/"+org+"/invitations").with(user(actor.toString()).roles("CUSTOMER")).contentType(MediaType.APPLICATION_JSON).content("{\"phoneNumber\":\"8999000001\",\"role\":\"STAFF\"}")).andExpect(status().isForbidden());}
    @Test void managerCannotChangeRoles() throws Exception {role(OrganisationRole.MANAGER);mvc.perform(patch("/api/v1/organisations/"+org+"/members/"+UUID.randomUUID()).with(user(actor.toString()).roles("CUSTOMER")).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"STAFF\"}")).andExpect(status().isForbidden());}
    @Test void adminCannotInviteOwner() throws Exception {role(OrganisationRole.ADMIN);mvc.perform(post("/api/v1/organisations/"+org+"/invitations").with(user(actor.toString()).roles("CUSTOMER")).contentType(MediaType.APPLICATION_JSON).content("{\"phoneNumber\":\"8999000001\",\"role\":\"OWNER\"}")).andExpect(status().isBadRequest());}
    @Test void unauthenticatedCannotList() throws Exception {mvc.perform(get("/api/v1/organisations")).andExpect(status().isForbidden());}
    @Test void rateLimitIncludesRetryAfter() throws Exception {when(users.findById(actor)).thenReturn(Optional.of(com.fooddelivery.identity.entity.AppUser.builder().id(actor).isActive(true).build()));doThrow(new OrganisationRateLimitException(60)).when(limits).create(actor);mvc.perform(post("/api/v1/organisations").with(user(actor.toString()).roles("CUSTOMER")).contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Acme\"}")).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"));}
}
