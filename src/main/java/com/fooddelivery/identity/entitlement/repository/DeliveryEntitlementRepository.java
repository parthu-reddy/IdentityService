package com.fooddelivery.identity.entitlement.repository;

import com.fooddelivery.identity.entitlement.entity.DeliveryEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DeliveryEntitlementRepository extends JpaRepository<DeliveryEntitlement,UUID> { }
