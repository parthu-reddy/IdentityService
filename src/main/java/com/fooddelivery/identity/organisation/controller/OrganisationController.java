package com.fooddelivery.identity.organisation.controller;

import com.fooddelivery.identity.organisation.service.*;
import com.fooddelivery.identity.organisation.dto.OrganisationRequests.*;
import com.fooddelivery.identity.organisation.dto.OrganisationViews.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Slice;
import org.springframework.http.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/organisations") @lombok.RequiredArgsConstructor
public class OrganisationController {
    private final OrganisationService service;
    private final OrganisationInvitationService invitations;
    @PostMapping @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganisationView> create(Authentication auth,@Valid @RequestBody Name body){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(auth,body.displayName()));}
    @GetMapping @PreAuthorize("isAuthenticated()")
    public Slice<OrganisationView> list(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(auth,page,size);}
    @GetMapping("/{organisationId}") @PreAuthorize("isAuthenticated()")
    public OrganisationView get(Authentication auth,@PathVariable UUID organisationId){return service.get(auth,organisationId);}
    @PatchMapping("/{organisationId}") @PreAuthorize("isAuthenticated()")
    public OrganisationView rename(Authentication auth,@PathVariable UUID organisationId,@Valid @RequestBody Name body){return service.rename(auth,organisationId,body.displayName());}
    @GetMapping("/{organisationId}/members") @PreAuthorize("isAuthenticated()")
    public Slice<MemberView> members(Authentication auth,@PathVariable UUID organisationId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.memberList(auth,organisationId,page,size);}
    @PatchMapping("/{organisationId}/members/{userId}") @PreAuthorize("isAuthenticated()")
    public MemberView role(Authentication auth,@PathVariable UUID organisationId,@PathVariable UUID userId,@Valid @RequestBody ChangeRole body){return service.changeRole(auth,organisationId,userId,body.role());}
    @DeleteMapping("/{organisationId}/members/{userId}") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> remove(Authentication auth,@PathVariable UUID organisationId,@PathVariable UUID userId){service.remove(auth,organisationId,userId);return ResponseEntity.ok().build();}
    @PostMapping("/{organisationId}/ownership-transfer") @PreAuthorize("isAuthenticated()")
    public OrganisationView transfer(Authentication auth,@PathVariable UUID organisationId,@Valid @RequestBody Transfer body){return service.transfer(auth,organisationId,body.userId());}
    @PostMapping("/{organisationId}/invitations") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InvitationView> invite(Authentication auth,@PathVariable UUID organisationId,@Valid @RequestBody Invite body){return ResponseEntity.status(HttpStatus.CREATED).body(invitations.invite(auth,organisationId,body.phoneNumber(),body.role()));}
    @GetMapping("/{organisationId}/invitations") @PreAuthorize("isAuthenticated()")
    public Slice<InvitationView> invitations(Authentication auth,@PathVariable UUID organisationId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return invitations.list(auth,organisationId,page,size);}
    @DeleteMapping("/{organisationId}/invitations/{invitationId}") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> revoke(Authentication auth,@PathVariable UUID organisationId,@PathVariable UUID invitationId){invitations.revoke(auth,organisationId,invitationId);return ResponseEntity.ok().build();}
}
