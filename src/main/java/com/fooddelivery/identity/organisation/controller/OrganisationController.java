package com.fooddelivery.identity.organisation.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.identity.organisation.dto.OrganisationRequests.ChangeRole;
import com.fooddelivery.identity.organisation.dto.OrganisationRequests.Invite;
import com.fooddelivery.identity.organisation.dto.OrganisationRequests.Name;
import com.fooddelivery.identity.organisation.dto.OrganisationRequests.Transfer;
import com.fooddelivery.identity.organisation.dto.OrganisationViews.InvitationView;
import com.fooddelivery.identity.organisation.dto.OrganisationViews.MemberView;
import com.fooddelivery.identity.organisation.dto.OrganisationViews.OrganisationView;
import com.fooddelivery.identity.organisation.service.OrganisationInvitationService;
import com.fooddelivery.identity.organisation.service.OrganisationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Organisations and their members, for the signed-in person. Every body is the platform ApiResponse. */
@RestController
@RequestMapping("/api/v1/organisations")
@PreAuthorize("isAuthenticated()")
@lombok.RequiredArgsConstructor
public class OrganisationController {
    private final OrganisationService service;
    private final OrganisationInvitationService invitations;

    @PostMapping
    public ResponseEntity<ApiResponse<OrganisationView>> create(Authentication auth, @Valid @RequestBody Name body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.create(auth, body.displayName()), "Organisation created"));
    }

    @GetMapping
    public ApiResponse<Slice<OrganisationView>> list(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(auth, page, size), "Organisations");
    }

    @GetMapping("/{organisationId}")
    public ApiResponse<OrganisationView> get(Authentication auth, @PathVariable UUID organisationId) {
        return ApiResponse.success(service.get(auth, organisationId), "Organisation");
    }

    @PatchMapping("/{organisationId}")
    public ApiResponse<OrganisationView> rename(
            Authentication auth, @PathVariable UUID organisationId, @Valid @RequestBody Name body) {
        return ApiResponse.success(service.rename(auth, organisationId, body.displayName()), "Organisation renamed");
    }

    @GetMapping("/{organisationId}/members")
    public ApiResponse<Slice<MemberView>> members(
            Authentication auth,
            @PathVariable UUID organisationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.memberList(auth, organisationId, page, size), "Members");
    }

    @PatchMapping("/{organisationId}/members/{userId}")
    public ApiResponse<MemberView> role(
            Authentication auth,
            @PathVariable UUID organisationId,
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeRole body) {
        return ApiResponse.success(service.changeRole(auth, organisationId, userId, body.role()), "Role changed");
    }

    @DeleteMapping("/{organisationId}/members/{userId}")
    public ApiResponse<Void> remove(Authentication auth, @PathVariable UUID organisationId, @PathVariable UUID userId) {
        service.remove(auth, organisationId, userId);
        return ApiResponse.success(null, "Member removed");
    }

    @PostMapping("/{organisationId}/ownership-transfer")
    public ApiResponse<OrganisationView> transfer(
            Authentication auth, @PathVariable UUID organisationId, @Valid @RequestBody Transfer body) {
        return ApiResponse.success(service.transfer(auth, organisationId, body.userId()), "Ownership transferred");
    }

    @PostMapping("/{organisationId}/invitations")
    public ResponseEntity<ApiResponse<InvitationView>> invite(
            Authentication auth, @PathVariable UUID organisationId, @Valid @RequestBody Invite body) {
        InvitationView invitation = invitations.invite(auth, organisationId, body.phoneNumber(), body.role());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(invitation, "Invitation sent"));
    }

    @GetMapping("/{organisationId}/invitations")
    public ApiResponse<Slice<InvitationView>> invitations(
            Authentication auth,
            @PathVariable UUID organisationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(invitations.list(auth, organisationId, page, size), "Invitations");
    }

    @DeleteMapping("/{organisationId}/invitations/{invitationId}")
    public ApiResponse<Void> revoke(Authentication auth, @PathVariable UUID organisationId, @PathVariable UUID invitationId) {
        invitations.revoke(auth, organisationId, invitationId);
        return ApiResponse.success(null, "Invitation revoked");
    }
}
