package com.fooddelivery.identity.organisation.service;

import com.fooddelivery.common.constants.RedisKeyConstants;
import com.fooddelivery.common.service.RateLimitingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.UUID;

@Component @lombok.RequiredArgsConstructor @lombok.extern.slf4j.Slf4j
public class OrganisationRateLimits {
    private final RateLimitingService limiter;
    @Value("${identity.organisations.rate-limits.create:5}") private int create = 5;
    @Value("${identity.organisations.rate-limits.invite-per-organisation:20}") private int inviteOrganisation = 20;
    @Value("${identity.organisations.rate-limits.invite-per-user:10}") private int inviteUser = 10;
    @Value("${identity.organisations.rate-limits.respond:30}") private int respond = 30;
    @Value("${identity.organisations.rate-limits.create-window:PT24H}") private Duration createWindow = Duration.ofDays(1);
    @Value("${identity.organisations.rate-limits.invite-window:PT1H}") private Duration inviteWindow = Duration.ofHours(1);
    @Value("${identity.organisations.rate-limits.respond-window:PT1H}") private Duration respondWindow = Duration.ofHours(1);
    public void create(UUID user) { consume(RedisKeyConstants.RL_ORG_CREATE + user, create, createWindow); }
    public void invite(UUID org, UUID user) {
        consume(RedisKeyConstants.RL_ORG_INVITE + org, inviteOrganisation, inviteWindow);
        consume(RedisKeyConstants.RL_ORG_INVITE_USER + user, inviteUser, inviteWindow);
    }
    public void respond(UUID user) { consume(RedisKeyConstants.RL_ORG_INVITE_RESPOND + user, respond, respondWindow); }
    private void consume(String key, int capacity, Duration window) {
        var probe = limiter.resolveBucket(key, capacity, capacity, window).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            log.warn("Organisation rate limit refused key={}", key);
            throw new OrganisationRateLimitException(Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L));
        }
    }
}
