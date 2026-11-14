package com.fooddelivery.identity.entitlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.identity.entitlement.dto.PortalViews.*;
import com.fooddelivery.identity.organisation.dto.OrganisationViews.MemberView;
import com.fooddelivery.common.enums.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PortalWireContractTest {
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();

    @Test void optionalPortalAndBusinessReasonsAreOmittedRatherThanInvalidNullStrings() throws Exception {
        var business=new BusinessView(BusinessType.RESTAURANT,UUID.randomUUID(),ApplicationStatus.APPROVED,null);
        var org=new OrganisationPortal(UUID.randomUUID(),"Team",OrganisationRole.OWNER,OrganisationStatus.ACTIVE,List.of(business));
        var json=mapper.readTree(mapper.writeValueAsString(new PortalView(Portal.RESTAURANT,State.AVAILABLE,List.of(org),null,null)));
        assertFalse(json.has("reason"));
        assertFalse(json.has("kitchenRoles"),"only an available KITCHEN tile names roles");
        assertFalse(json.path("organisations").get(0).path("businesses").get(0).has("reason"));
        assertEquals("AVAILABLE",json.path("state").asText());
    }

    @Test void aMemberWithoutACompletedProfileStillHasAValidGeneratedClientShape() throws Exception {
        var member=new MemberView(UUID.randomUUID(),null,"8999000001",OrganisationRole.MANAGER,MembershipStatus.ACTIVE,Instant.now());
        var json=mapper.readTree(mapper.writeValueAsString(member));
        assertFalse(json.has("name"));
        assertEquals("MANAGER",json.path("role").asText());
        assertEquals("8999000001",json.path("phoneNumber").asText());
    }

    @Test void kitchenRolesTravelAsEnumNames() throws Exception {
        var json=mapper.readTree(mapper.writeValueAsString(new PortalView(Portal.KITCHEN,State.AVAILABLE,List.of(),null,List.of(KitchenRole.CHEF,KitchenRole.WAITER))));
        assertEquals("[\"CHEF\",\"WAITER\"]",json.path("kitchenRoles").toString());
    }
}
