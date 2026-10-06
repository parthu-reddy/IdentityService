package com.fooddelivery.identity.organisation.controller;

import com.fooddelivery.common.dto.organisation.MembershipDto;
import com.fooddelivery.identity.organisation.service.LocalOrganisationAccessPolicy;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/internal/users")
@PreAuthorize("hasAnyRole('SERVICE','ADMIN')")
@lombok.RequiredArgsConstructor
public class InternalUserOrganisationsController {
    private final LocalOrganisationAccessPolicy service;

    @GetMapping("/{userId}/organisations")
    public List<MembershipDto> memberships(@PathVariable UUID userId) {
        return service.userMemberships(userId);
    }
}
