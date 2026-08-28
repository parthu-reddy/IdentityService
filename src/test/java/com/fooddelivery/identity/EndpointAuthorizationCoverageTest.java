package com.fooddelivery.identity;

import com.fooddelivery.common.test.EndpointAuthorizationCoverage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every HTTP endpoint in this module must carry an authorization rule, at class or method level.
 *
 * <p>Added 2026-08-28 after {@code InternalUserController} was found with authorization on none of
 * its six endpoints -- including {@code POST /{id}/roles}, which grants a role. It was reachable in
 * process through {@code IdentityMcpService.addRole(userId, roleName)}, an MCP tool taking both the
 * target user and the role from its caller: a self-service route to ROLE_ADMIN.
 *
 * <p>Four other modules already had this test. IdentityService did not, which is why the gap
 * survived. Sibling controllers here were annotated; nothing required the next one to be.
 *
 * <p>Needs no Spring context, no database and no broker: it is a reflective scan over the compiled
 * controllers, so an infrastructure failure cannot skip it.
 */
class EndpointAuthorizationCoverageTest {

    private static final String BASE_PACKAGE = "com.fooddelivery";

    /** Floor, not an exact count: adding endpoints must not break the build. */
    private static final int MINIMUM_EXPECTED_ENDPOINTS = 5;

    /**
     * Endpoints that are intentionally anonymous. Adding one here is a deliberate, reviewable act;
     * forgetting {@code @PreAuthorize} is not.
     */
    private static final Set<String> INTENTIONALLY_ANONYMOUS = Set.of();

    /**
     * A scan that discovers no controllers would report "nothing unprotected" and pass while guarding
     * nothing. Assert it actually found endpoints before trusting what it says about them.
     */
    @Test
    void theScanActuallyFindsEndpoints() {
        assertThat(EndpointAuthorizationCoverage.countEndpoints(BASE_PACKAGE))
                .describedAs("endpoints discovered under " + BASE_PACKAGE)
                .isGreaterThan(MINIMUM_EXPECTED_ENDPOINTS);
    }

    @Test
    void everyEndpointCarriesAnAuthorizationRule() {
        List<EndpointAuthorizationCoverage.Unprotected> unprotected =
                EndpointAuthorizationCoverage.scan(BASE_PACKAGE, INTENTIONALLY_ANONYMOUS);

        assertThat(unprotected)
                .describedAs("Endpoints with no @PreAuthorize/@Secured/@RolesAllowed at class or "
                        + "method level. Add an authorization rule, or -- if the endpoint really is "
                        + "public -- add it to INTENTIONALLY_ANONYMOUS with a comment saying why.")
                .isEmpty();
    }
}
