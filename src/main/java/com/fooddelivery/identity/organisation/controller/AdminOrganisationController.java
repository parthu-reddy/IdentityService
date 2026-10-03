package com.fooddelivery.identity.organisation.controller;

import com.fooddelivery.identity.organisation.service.OrganisationService;
import com.fooddelivery.identity.organisation.dto.OrganisationRequests.Reason;
import com.fooddelivery.identity.organisation.dto.OrganisationViews.OrganisationView;
import com.fooddelivery.identity.organisation.dto.OrganisationViews.AdminOrganisationView;
import com.fooddelivery.common.enums.OrganisationStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Slice;
import jakarta.validation.Valid;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/internal/admin/organisations") @PreAuthorize("hasRole('ADMIN')") @lombok.RequiredArgsConstructor
public class AdminOrganisationController {
    private final OrganisationService service;
    @GetMapping public Slice<OrganisationView> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.adminList(page,size);}
    @GetMapping("/{organisationId}") public AdminOrganisationView get(@PathVariable UUID organisationId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return new AdminOrganisationView(service.adminGet(organisationId),service.adminMembers(organisationId,page,size));}
    @GetMapping("/{organisationId}/members") public Slice<com.fooddelivery.identity.organisation.dto.OrganisationViews.MemberView> members(@PathVariable UUID organisationId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.adminMembers(organisationId,page,size);}
    @PostMapping("/{organisationId}/suspend") public OrganisationView suspend(Authentication auth,@PathVariable UUID organisationId,@Valid @RequestBody Reason body){return service.setStatus(auth,organisationId,OrganisationStatus.SUSPENDED,body.reason());}
    @PostMapping("/{organisationId}/reinstate") public OrganisationView reinstate(Authentication auth,@PathVariable UUID organisationId,@Valid @RequestBody Reason body){return service.setStatus(auth,organisationId,OrganisationStatus.ACTIVE,body.reason());}
}
