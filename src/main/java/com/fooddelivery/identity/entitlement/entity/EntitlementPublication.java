package com.fooddelivery.identity.entitlement.entity;

import jakarta.persistence.*;
import java.util.UUID;

/** Durable retries for the after-commit Redis mirror; removed only when this version was written. */
@Entity @Table(name="entitlement_version_publications")
@lombok.Getter @lombok.Setter @lombok.NoArgsConstructor @lombok.AllArgsConstructor
public class EntitlementPublication {
    @Id @Column(name="user_id") private UUID userId;
    @Column(name="entitlements_version",nullable=false) private long entitlementsVersion;
}
