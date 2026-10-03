package com.fooddelivery.identity.organisation;

import com.fooddelivery.common.service.RateLimitingService;
import com.fooddelivery.common.constants.RedisKeyConstants;
import com.fooddelivery.identity.organisation.service.*;
import io.github.bucket4j.*;
import org.junit.jupiter.api.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Bucket4j consumption; no dev profile or permissive Redis implementation. */
class OrganisationRateLimitsTest {
    RateLimitingService backend;
    OrganisationRateLimits limits;
    @BeforeEach void setup(){
        backend=mock(RateLimitingService.class);limits=new OrganisationRateLimits(backend);
        Map<String,Bucket> buckets=new HashMap<>();
        when(backend.resolveBucket(anyString(),anyInt(),anyInt(),any(Duration.class))).thenAnswer(i ->
            buckets.computeIfAbsent(i.getArgument(0),k -> Bucket.builder().addLimit(Bandwidth.classic(i.getArgument(1,Integer.class).longValue(),Refill.intervally(i.getArgument(2,Integer.class).longValue(),i.getArgument(3,Duration.class)))).build()));
    }
    @Test void sixthCreateIsRejectedWithPositiveRetryAfter(){UUID user=UUID.randomUUID();for(int i=0;i<5;i++){limits.create(user);}assertTrue(assertThrows(OrganisationRateLimitException.class,()->limits.create(user)).retryAfter()>0);verify(backend,times(6)).resolveBucket(RedisKeyConstants.RL_ORG_CREATE+user,5,5,Duration.ofDays(1));}
    @Test void invitationLimitsApplyToBothOrganisationAndInviter(){UUID org=UUID.randomUUID(),user=UUID.randomUUID();for(int i=0;i<10;i++){limits.invite(org,user);}assertThrows(OrganisationRateLimitException.class,()->limits.invite(org,user));verify(backend,times(11)).resolveBucket(RedisKeyConstants.RL_ORG_INVITE_USER+user,10,10,Duration.ofHours(1));}
    @Test void differentInvitersShareOrganisationLimit(){UUID org=UUID.randomUUID();for(int i=0;i<20;i++){limits.invite(org,UUID.randomUUID());}assertThrows(OrganisationRateLimitException.class,()->limits.invite(org,UUID.randomUUID()));verify(backend,times(21)).resolveBucket(RedisKeyConstants.RL_ORG_INVITE+org,20,20,Duration.ofHours(1));}
    @Test void thirtyFirstResponseIsRejected(){UUID user=UUID.randomUUID();for(int i=0;i<30;i++){limits.respond(user);}assertThrows(OrganisationRateLimitException.class,()->limits.respond(user));verify(backend,times(31)).resolveBucket(RedisKeyConstants.RL_ORG_INVITE_RESPOND+user,30,30,Duration.ofHours(1));}
}
