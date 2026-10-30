package com.fooddelivery.identity.dinein.controller;

import com.fooddelivery.identity.dinein.dto.DineInConsentRequests.*;
import com.fooddelivery.identity.dinein.service.DineInConsentService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Service-only: the gateway refuses /api/v1/internal/ from outside, and no administrator uses it. */
@RestController
@RequestMapping("/api/v1/internal/identity/dine-in-consents")
@PreAuthorize("hasRole('SERVICE')")
@lombok.RequiredArgsConstructor
public class InternalDineInConsentController {
    private final DineInConsentService consents;

    @PostMapping
    public ResponseEntity<DineInConsentRequested> requestDineInConsent(
            @Valid @RequestBody DineInConsentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(consents.request(request));
    }

    @PostMapping("/{consentId}/verify")
    public ResponseEntity<DineInConsentVerified> verifyDineInConsent(
            @PathVariable UUID consentId, @Valid @RequestBody DineInConsentVerification request) {
        return ResponseEntity.ok(consents.verify(consentId, request.code()));
    }
}
