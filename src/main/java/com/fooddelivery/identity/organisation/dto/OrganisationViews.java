package com.fooddelivery.identity.organisation.dto;

import com.fooddelivery.common.enums.*;
import com.fooddelivery.identity.organisation.entity.InvitationStatus;
import java.util.UUID;
import java.time.Instant;

public final class OrganisationViews {
    private OrganisationViews() { }
    public record OrganisationView(UUID id, String displayName, OrganisationStatus status,
        OrganisationRole myRole, Instant createdAt, Instant updatedAt) { }
    public record AdminOrganisationView(OrganisationView organisation,
        org.springframework.data.domain.Slice<MemberView> members) { }
    public record MemberView(UUID userId, String name, String phoneNumber, OrganisationRole role,
        MembershipStatus status, Instant createdAt) { }
    public record InvitationView(UUID id, UUID organisationId, String phoneNumber, OrganisationRole role,
        InvitationStatus status, Instant expiresAt, Instant createdAt) { }
}
