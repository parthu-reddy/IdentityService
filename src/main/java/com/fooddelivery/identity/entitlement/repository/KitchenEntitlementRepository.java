package com.fooddelivery.identity.entitlement.repository;

import com.fooddelivery.common.enums.KitchenStaffStatus;
import com.fooddelivery.identity.entitlement.entity.KitchenEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

/** Served by the kitchen_entitlements indexes (user_id,status), (phone_number,status), (organisation_id,status). */
public interface KitchenEntitlementRepository extends JpaRepository<KitchenEntitlement,UUID> {
    /** Live rows that grant KITCHEN, before and after the person claims them. */
    Set<KitchenStaffStatus> LIVE = EnumSet.of(KitchenStaffStatus.INVITED, KitchenStaffStatus.ACTIVE);

    /** Live rows of these people in one read: bound to one of them, or still unbound under one of their phones. */
    @Query("""
        select k from KitchenEntitlement k where k.status in :statuses
        and (k.userId in :userIds or (k.userId is null and k.phoneNumber in :phones))
        """)
    List<KitchenEntitlement> findLive(@Param("userIds") Collection<UUID> userIds, @Param("phones") Collection<String> phones,
                                      @Param("statuses") Collection<KitchenStaffStatus> statuses);
    List<KitchenEntitlement> findAllByOrganisationIdAndStatusIn(UUID organisationId, Collection<KitchenStaffStatus> statuses);
}
