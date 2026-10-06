package com.fooddelivery.identity.organisation.controller;

import com.fooddelivery.common.dto.organisation.MembershipDto;
import com.fooddelivery.common.dto.organisation.OrganisationBusinessDto;
import com.fooddelivery.common.enums.OrganisationPermission;
import com.fooddelivery.identity.entitlement.service.OrganisationBusinessQuery;
import com.fooddelivery.identity.organisation.service.LocalOrganisationAccessPolicy;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/internal/organisations")
@PreAuthorize("hasAnyRole('SERVICE','ADMIN')")
@lombok.RequiredArgsConstructor
public class InternalOrganisationController {
    private final LocalOrganisationAccessPolicy service;
    private final OrganisationBusinessQuery businesses;

    @GetMapping("/{organisationId}/members/{userId}")
    public MembershipDto membership(@PathVariable UUID organisationId, @PathVariable UUID userId) {
        return service.membership(organisationId, userId);
    }

    @GetMapping("/{organisationId}/members")
    public List<UUID> members(
            @PathVariable UUID organisationId, @RequestParam OrganisationPermission permission) {
        return service.memberIds(organisationId, permission);
    }

    @GetMapping("/{organisationId}/businesses")
    public List<OrganisationBusinessDto> businesses(@PathVariable UUID organisationId) {
        return businesses.of(organisationId);
    }
}
