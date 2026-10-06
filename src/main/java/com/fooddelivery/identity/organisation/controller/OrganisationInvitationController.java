package com.fooddelivery.identity.organisation.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.identity.organisation.dto.OrganisationViews.InvitationView;
import com.fooddelivery.identity.organisation.service.OrganisationInvitationService;
import java.util.UUID;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in person's own organisation invitations. Every body is the platform ApiResponse. */
@RestController
@RequestMapping("/api/v1/organisation-invitations")
@PreAuthorize("isAuthenticated()")
@lombok.RequiredArgsConstructor
public class OrganisationInvitationController {
    private final OrganisationInvitationService service;

    @GetMapping
    public ApiResponse<Slice<InvitationView>> mine(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.mine(auth, page, size), "Invitations");
    }

    @PostMapping("/{invitationId}/accept")
    public ApiResponse<InvitationView> accept(Authentication auth, @PathVariable UUID invitationId) {
        return ApiResponse.success(service.respond(auth, invitationId, true), "Invitation accepted");
    }

    @PostMapping("/{invitationId}/decline")
    public ApiResponse<InvitationView> decline(Authentication auth, @PathVariable UUID invitationId) {
        return ApiResponse.success(service.respond(auth, invitationId, false), "Invitation declined");
    }
}
