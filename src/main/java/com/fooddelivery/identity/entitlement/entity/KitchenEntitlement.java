package com.fooddelivery.identity.entitlement.entity;

import com.fooddelivery.common.enums.KitchenStaffStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Projection of one restaurant-service kitchen staff row (kitchen-events); the phone grants KITCHEN until user_id is bound. */
@Entity @Table(name="kitchen_entitlements")
@lombok.Getter @lombok.Setter @lombok.NoArgsConstructor @lombok.AllArgsConstructor @lombok.Builder
public class KitchenEntitlement {
    @Id @Column(name="staff_id") private UUID staffId;
    @Column(name="organisation_id",nullable=false) private UUID organisationId;
    @Column(name="outlet_id",nullable=false) private UUID outletId;
    @Column(name="phone_number",nullable=false,length=10) private String phoneNumber;
    @Column(name="user_id") private UUID userId;
    @Column(name="is_manager",nullable=false) private boolean manager;
    @Column(name="is_chef",nullable=false) private boolean chef;
    @Column(name="is_waiter",nullable=false) private boolean waiter;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private KitchenStaffStatus status;
    @Column(name="staff_version",nullable=false) private long staffVersion;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
}
