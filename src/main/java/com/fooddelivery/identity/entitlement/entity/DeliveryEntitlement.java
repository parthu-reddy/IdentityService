package com.fooddelivery.identity.entitlement.entity;

import com.fooddelivery.common.enums.ApplicationStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="delivery_entitlements")
@lombok.Getter @lombok.Setter @lombok.NoArgsConstructor @lombok.AllArgsConstructor @lombok.Builder
public class DeliveryEntitlement {
    @Id @Column(name="user_id") private UUID userId;
    @Enumerated(EnumType.STRING) @Column(name="application_status",nullable=false,length=20) private ApplicationStatus applicationStatus;
    @Column(length=500) private String reason;
    @Column(name="application_version",nullable=false) private long applicationVersion;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
}
