package com.fooddelivery.identity.organisation.controller;

import com.fooddelivery.identity.organisation.service.LocalOrganisationAccessPolicy;
import com.fooddelivery.common.dto.organisation.MembershipDto;
import com.fooddelivery.common.enums.OrganisationPermission;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.*;

@RestController @RequestMapping("/api/v1/internal/organisations") @PreAuthorize("hasAnyRole('SERVICE','ADMIN')") @lombok.RequiredArgsConstructor
public class InternalOrganisationController {
    private final LocalOrganisationAccessPolicy service;
    @GetMapping("/{organisationId}/members/{userId}")
    public MembershipDto membership(@PathVariable UUID organisationId,@PathVariable UUID userId){return service.membership(organisationId,userId);}
    @GetMapping("/{organisationId}/members")
    public List<UUID> members(@PathVariable UUID organisationId,@RequestParam OrganisationPermission permission){return service.memberIds(organisationId,permission);}
}
