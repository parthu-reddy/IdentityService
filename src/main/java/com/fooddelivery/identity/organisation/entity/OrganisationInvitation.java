package com.fooddelivery.identity.organisation.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
import com.fooddelivery.common.enums.*;

@Entity @Table(name="organisation_invitations") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrganisationInvitation {
    @Id private UUID id;
    @Column(name="organisation_id", nullable=false) private UUID organisationId;
    @Column(name="phone_number", nullable=false, length=20) private String phoneNumber;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private OrganisationRole role;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private InvitationStatus status;
    @Column(name="invited_by", nullable=false) private UUID invitedBy;
    @Column(name="responded_by") private UUID respondedBy;
    @Column(name="expires_at", nullable=false) private Instant expiresAt;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
}
