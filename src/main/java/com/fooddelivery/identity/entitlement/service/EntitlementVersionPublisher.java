package com.fooddelivery.identity.entitlement.service;

import com.fooddelivery.common.constants.RedisKeyConstants;
import com.fooddelivery.common.enums.MembershipStatus;
import com.fooddelivery.identity.entitlement.entity.EntitlementPublication;
import com.fooddelivery.identity.entitlement.repository.EntitlementPublicationRepository;
import com.fooddelivery.identity.organisation.repository.OrganisationMemberRepository;
import com.fooddelivery.identity.port.CachePort;
import com.fooddelivery.identity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import java.time.Duration;
import java.util.*;

/**
 * @replication-safe: retry rows are durable; monotonic Redis writes and conditional
 * acknowledgements make concurrent replica retries idempotent without a scheduler lock.
 */
@Service @lombok.extern.slf4j.Slf4j
public class EntitlementVersionPublisher {
    private final UserRepository users;
    private final OrganisationMemberRepository members;
    private final EntitlementPublicationRepository publications;
    private final CachePort cache;
    private final TransactionTemplate acknowledgement;
    @Value("${jwt.expiration:86400000}") private long tokenLifetimeMs=86400000;
    @Value("${identity.admin-session.ttl:PT8H}") private Duration adminLifetime=Duration.ofHours(8);

    public EntitlementVersionPublisher(UserRepository users,OrganisationMemberRepository members,
            EntitlementPublicationRepository publications,CachePort cache,PlatformTransactionManager manager) {
        this.users=users;this.members=members;this.publications=publications;this.cache=cache;
        acknowledgement=new TransactionTemplate(manager);
        acknowledgement.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void bumpOrganisation(UUID id) {
        bump(members.findAllByOrganisationIdAndStatus(id,MembershipStatus.ACTIVE).stream().map(m -> m.getUserId()).toList());
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void bump(Collection<UUID> affected) {
        var ids=affected.stream().distinct().sorted().toList();
        if (ids.isEmpty()) return;
        users.bumpEntitlements(ids);
        var values=new LinkedHashMap<UUID,Long>();
        users.entitlementVersions(ids).forEach(row -> values.put((UUID)row[0],(Long)row[1]));
        publications.saveAll(values.entrySet().stream().map(e -> new EntitlementPublication(e.getKey(),e.getValue())).toList());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { publish(values); }
        });
    }
    @Scheduled(fixedDelayString="${identity.entitlements.retry-delay-ms:5000}")
    public void retryPending() {
        var pending=publications.pending(PageRequest.of(0,200));
        if (pending.isEmpty()) return;
        var values=new LinkedHashMap<UUID,Long>();
        pending.forEach(p -> values.put(p.getUserId(),p.getEntitlementsVersion()));
        publish(values);
    }
    private void publish(Map<UUID,Long> versions) {
        var values=new LinkedHashMap<String,String>();
        versions.forEach((id,version) -> values.put(RedisKeyConstants.ENTITLEMENTS_VERSION+id,Long.toString(version)));
        try {
            // One pipeline; each Lua write is monotonic and refreshes the 2 x token-lifetime TTL.
            cache.putVersions(values,Duration.ofMillis(Math.multiplyExact(2,Math.max(tokenLifetimeMs,adminLifetime.toMillis()))));
            acknowledgement.executeWithoutResult(status -> versions.forEach(publications::acknowledge));
        } catch (RuntimeException ex) {
            // DB truth and retry rows are already committed. Never log credentials or raw cache data.
            log.warn("Entitlement version publication pending retry users={} failure={}",versions.size(),ex.getClass().getSimpleName());
        }
    }
}
