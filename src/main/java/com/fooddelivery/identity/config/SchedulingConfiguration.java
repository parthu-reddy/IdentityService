package com.fooddelivery.identity.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} jobs for the running service.
 *
 * <p>Kept off {@code IdentityApplication} on purpose: every slice test ({@code @DataJpaTest},
 * {@code @WebMvcTest}) loads the main class's annotations, but excludes scanned configuration
 * classes like this one. On the main class it started the entitlement retry poller inside slice
 * contexts, where it raced the tests and broke their exact statement and cache-call counts.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfiguration {}
