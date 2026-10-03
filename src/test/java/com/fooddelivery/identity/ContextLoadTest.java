package com.fooddelivery.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real application context starts.
 *
 * <p>Added 2026-09-13. This service had no full-context test, so the CommonLibrary split was verified
 * here by reading poms rather than by watching Spring start. That matters because
 * {@code AutoConfiguration.imports} is resolved at startup and Spring Boot aborts on an entry it
 * cannot load: a compiler never sees it, and neither does a unit test that avoids a context.
 *
 * <p>Uses {@code contract-test}, the profile already configured to boot without external
 * infrastructure — H2 for Postgres, the config server off. Beans that need a real server are mocked
 * individually rather than by excluding auto-configuration, so everything common-library contributes
 * is still created for real.
 */
// jwt.expiration is required by AuthController and absent from the contract-test profile, which was
// written for narrow @SpringBootTest(classes = ...) contract tests that never build that controller.
// Supplied here rather than added to the shared profile, so the contract tests keep the exact
// configuration they were verified against.
@SpringBootTest(properties = "jwt.expiration=3600000")
// The OTP inspection endpoint is strictly e2e-profiled and the unsafe MCP tool bean is removed.
// Keep this production-like context free of either test-only capability.
@ActiveProfiles("contract-test")
class ContextLoadTest {

    // The contract-test profile excludes Redis auto-configuration, so nothing supplies these.
    // Mocked individually rather than by excluding more auto-configuration: the point of this test is
    // that everything else -- including every bean common-library contributes -- is created for real.
    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.fooddelivery.common.service.RateLimitingService rateLimitingService;

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertTrue(context.getBeanDefinitionCount() > 0, "an empty context is not a started one");
        org.junit.jupiter.api.Assertions.assertEquals(1, context.getBeansOfType(java.time.Clock.class).size());
        org.junit.jupiter.api.Assertions.assertEquals(java.time.ZoneOffset.UTC, context.getBean(java.time.Clock.class).getZone());
    }

    @Test
    void organisationPolicyUsesLocalDatabaseInsteadOfCallingIdentityOverFeign() {
        var policies=context.getBeansOfType(com.fooddelivery.common.security.organisation.OrganisationAccessPolicy.class);
        org.junit.jupiter.api.Assertions.assertEquals(1,policies.size());
        org.junit.jupiter.api.Assertions.assertInstanceOf(
            com.fooddelivery.identity.organisation.service.LocalOrganisationAccessPolicy.class,
            context.getBean("organisationAccessPolicy"));
    }

    @Test
    void e2eOtpHarnessIsAbsentWithoutTheE2eProfile() {
        assertFalse(context.containsBean("e2eOtpController"));
        assertTrue(context.getBeansOfType(
                com.fooddelivery.identity.service.E2eRunnerSecretVerifier.class).isEmpty());
    }

    @Test
    void organisationOutboxPublisherIsActuallyScheduled() {
        var schedulers=context.getBeansOfType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class);
        assertFalse(schedulers.isEmpty(), "The outbox bean alone does not publish; scheduling must be enabled");
        assertTrue(schedulers.values().stream().flatMap(s -> s.getScheduledTasks().stream())
                .map(t -> t.getTask().getRunnable())
                .anyMatch(r -> r instanceof org.springframework.scheduling.support.ScheduledMethodRunnable method
                        && method.getMethod().getDeclaringClass()==com.fooddelivery.common.outbox.service.OutboxProcessor.class
                        && method.getMethod().getName().equals("processOutboxEvents")),
                "The organisation outbox publisher must be scheduled in the real Identity context");
    }
}
