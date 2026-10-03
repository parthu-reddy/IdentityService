package com.fooddelivery.identity.organisation.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
import com.fooddelivery.common.enums.*;

@Entity @Table(name="organisations") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Organisation {
    @Id private UUID id;
    @Column(name="display_name", nullable=false, length=120) private String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private OrganisationStatus status;
    @Column(name="created_by", nullable=false) private UUID createdBy;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private Long version;
}
