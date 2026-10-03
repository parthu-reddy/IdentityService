package com.fooddelivery.identity.organisation.controller;

import com.fooddelivery.identity.organisation.service.OrganisationInvitationService;
import com.fooddelivery.identity.organisation.dto.OrganisationViews.InvitationView;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Slice;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/organisation-invitations") @lombok.RequiredArgsConstructor
public class OrganisationInvitationController {
    private final OrganisationInvitationService service;
    @GetMapping @PreAuthorize("isAuthenticated()")
    public Slice<InvitationView> mine(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.mine(auth,page,size);}
    @PostMapping("/{invitationId}/accept") @PreAuthorize("isAuthenticated()")
    public InvitationView accept(Authentication auth,@PathVariable UUID invitationId){return service.respond(auth,invitationId,true);}
    @PostMapping("/{invitationId}/decline") @PreAuthorize("isAuthenticated()")
    public InvitationView decline(Authentication auth,@PathVariable UUID invitationId){return service.respond(auth,invitationId,false);}
}
