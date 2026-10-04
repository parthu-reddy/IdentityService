package com.fooddelivery.identity.entitlement.entity;

import com.fooddelivery.common.enums.ApplicationStatus;
import com.fooddelivery.common.enums.BusinessType;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="business_entitlements")
@IdClass(BusinessEntitlement.Key.class)
@lombok.Getter @lombok.Setter @lombok.NoArgsConstructor @lombok.AllArgsConstructor @lombok.Builder
public class BusinessEntitlement {
    @Id @Enumerated(EnumType.STRING) @Column(name="business_type",length=30) private BusinessType businessType;
    @Id @Column(name="business_id") private UUID businessId;
    @Column(name="organisation_id",nullable=false) private UUID organisationId;
    @Enumerated(EnumType.STRING) @Column(name="application_status",nullable=false,length=20) private ApplicationStatus applicationStatus;
    @Column(length=500) private String reason;
    @Column(name="application_version",nullable=false) private long applicationVersion;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;

    @lombok.Data @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class Key implements Serializable {
        private BusinessType businessType;
        private UUID businessId;
    }
}
