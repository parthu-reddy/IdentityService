package com.fooddelivery.identity.entitlement.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.identity.entitlement.dto.PortalViews.PortalView;
import com.fooddelivery.identity.entitlement.service.PortalService;
import com.fooddelivery.identity.organisation.service.OrganisationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/users") @PreAuthorize("isAuthenticated()") @lombok.RequiredArgsConstructor
public class PortalController {
    private final PortalService portals;
    @GetMapping("/me/portals")
    public ApiResponse<List<PortalView>> portals(Authentication auth) {
        return ApiResponse.success(portals.portals(OrganisationService.userId(auth)),"Portal access retrieved");
    }
}
