package com.fooddelivery.identity.entitlement.repository;

import com.fooddelivery.identity.entitlement.entity.BusinessEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface BusinessEntitlementRepository extends JpaRepository<BusinessEntitlement,BusinessEntitlement.Key> {
    List<BusinessEntitlement> findAllByOrganisationIdIn(Collection<UUID> organisationIds);
}
