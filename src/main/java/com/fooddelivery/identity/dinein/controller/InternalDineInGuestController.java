package com.fooddelivery.identity.dinein.controller;

import com.fooddelivery.identity.dinein.dto.DineInGuestRequests.DineInGuestLinkRequest;
import com.fooddelivery.identity.dinein.dto.DineInGuestRequests.DineInGuestLinked;
import com.fooddelivery.identity.dinein.service.DineInGuestService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Service-only: the gateway refuses /api/v1/internal/ from outside, and no administrator uses it. */
@RestController
@RequestMapping("/api/v1/internal/identity/dine-in-guests")
@PreAuthorize("hasRole('SERVICE')")
@lombok.RequiredArgsConstructor
public class InternalDineInGuestController {
    private final DineInGuestService guests;

    /** 200, not 201: a phone already known is found, not created, and the same guest may be linked again. */
    @PostMapping
    public ResponseEntity<DineInGuestLinked> linkDineInGuest(@Valid @RequestBody DineInGuestLinkRequest request) {
        return ResponseEntity.ok(guests.link(request));
    }
}
