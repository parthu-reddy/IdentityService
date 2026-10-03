package com.fooddelivery.identity.organisation.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
import com.fooddelivery.common.enums.*;

@Entity @Table(name="organisation_members", uniqueConstraints=@UniqueConstraint(name="uq_organisation_member", columnNames={"organisation_id","user_id"})) @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrganisationMember {
    @Id private UUID id;
    @Column(name="organisation_id", nullable=false) private UUID organisationId;
    @Column(name="user_id", nullable=false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private OrganisationRole role;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private MembershipStatus status;
    @Column(name="added_by") private UUID addedBy;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private Long version;
}
