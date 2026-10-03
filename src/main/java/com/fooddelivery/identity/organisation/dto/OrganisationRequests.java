package com.fooddelivery.identity.organisation.dto;

import jakarta.validation.constraints.*;
import com.fooddelivery.common.enums.OrganisationRole;
import java.util.UUID;

public final class OrganisationRequests {
    private OrganisationRequests() { }
    public record Name(@NotBlank @Size(max=120) String displayName) { }
    public record Invite(@NotNull @Pattern(regexp="[0-9]{10}") String phoneNumber, @NotNull OrganisationRole role) { }
    public record ChangeRole(@NotNull OrganisationRole role) { }
    public record Transfer(@NotNull UUID userId) { }
    public record Reason(@NotBlank @Size(min=10,max=500) String reason) { }
}
