package com.fooddelivery.identity.entitlement.service;

import com.fooddelivery.common.enums.BusinessType;
import com.fooddelivery.common.enums.KitchenRole;
import com.fooddelivery.common.event.KitchenStaffChangedEvent;
import com.fooddelivery.common.event.application.*;
import com.fooddelivery.identity.entitlement.entity.*;
import com.fooddelivery.identity.entitlement.repository.*;
import com.fooddelivery.identity.organisation.repository.OrganisationRepository;
import com.fooddelivery.identity.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@lombok.RequiredArgsConstructor
public class EntitlementProjectionService {
    private final BusinessEntitlementRepository businesses;
    private final DeliveryEntitlementRepository delivery;
    private final KitchenEntitlementRepository kitchens;
    private final OrganisationRepository organisations;
    private final UserRepository users;
    private final EntitlementVersionPublisher versions;
    private final Clock clock;

    @Transactional
    public boolean restaurant(RestaurantApplicationStatusChangedEvent event) {
        organisations
                .lockById(event.organisationId())
                .orElseThrow(
                        () -> new IllegalStateException("Application organisation is unavailable"));
        var key = new BusinessEntitlement.Key(BusinessType.RESTAURANT, event.brandId());
        var existing = businesses.findById(key);
        if (existing.isPresent()
                && existing.get().getApplicationVersion() >= event.applicationVersion())
            return false;
        var row =
                existing.orElseGet(
                        () ->
                                BusinessEntitlement.builder()
                                        .businessType(BusinessType.RESTAURANT)
                                        .businessId(event.brandId())
                                        .organisationId(event.organisationId())
                                        .build());
        if (!event.organisationId().equals(row.getOrganisationId()))
            throw new IllegalArgumentException("Application ownership cannot change");
        row.setApplicationStatus(event.status());
        row.setApplicationVersion(event.applicationVersion());
        row.setReason(event.reason());
        row.setUpdatedAt(clock.instant());
        businesses.saveAndFlush(row);
        versions.bumpOrganisation(event.organisationId());
        return true;
    }

    @Transactional
    public boolean delivery(DeliveryApplicationStatusChangedEvent event) {
        users.lockById(event.executiveId())
                .orElseThrow(() -> new IllegalStateException("Application user is unavailable"));
        var existing = delivery.findById(event.executiveId());
        if (existing.isPresent()
                && existing.get().getApplicationVersion() >= event.applicationVersion())
            return false;
        var row =
                existing.orElseGet(
                        () -> DeliveryEntitlement.builder().userId(event.executiveId()).build());
        row.setApplicationStatus(event.status());
        row.setApplicationVersion(event.applicationVersion());
        row.setReason(event.reason());
        row.setUpdatedAt(clock.instant());
        delivery.saveAndFlush(row);
        versions.bump(List.of(event.executiveId()));
        return true;
    }

    /**
     * Monotonic on staffVersion, so duplicates and reordered events are harmless. An unknown organisation throws (retry,
     * then DLT) rather than being accepted silently. Bumps the bound user, the previously bound user and whoever
     * currently signs in with the phone (an unbound row grants KITCHEN by phone); restaurant-service never changes a
     * staff row's phone.
     */
    @Transactional
    public boolean kitchen(KitchenStaffChangedEvent event) {
        organisations
                .lockById(event.getOrganisationId())
                .orElseThrow(
                        () -> new IllegalStateException("Kitchen staff organisation is unavailable"));
        var existing = kitchens.findById(event.getStaffId());
        if (existing.isPresent()
                && existing.get().getStaffVersion() >= event.getStaffVersion()) return false;
        var row =
                existing.orElseGet(
                        () ->
                                KitchenEntitlement.builder()
                                        .staffId(event.getStaffId())
                                        .organisationId(event.getOrganisationId())
                                        .build());
        if (!event.getOrganisationId().equals(row.getOrganisationId()))
            throw new IllegalArgumentException("Kitchen staff organisation cannot change");
        var previousUser = row.getUserId();
        row.setOutletId(event.getOutletId());
        row.setPhoneNumber(event.getPhoneNumber());
        row.setUserId(event.getUserId());
        row.setManager(event.getRoles().contains(KitchenRole.MANAGER));
        row.setChef(event.getRoles().contains(KitchenRole.CHEF));
        row.setStatus(event.getStatus());
        row.setStaffVersion(event.getStaffVersion());
        row.setUpdatedAt(clock.instant());
        kitchens.saveAndFlush(row);
        var affected = new HashSet<UUID>();
        affected.add(event.getUserId());
        affected.add(previousUser);
        users.findByPhoneNumber(event.getPhoneNumber()).ifPresent(u -> affected.add(u.getId()));
        affected.remove(null);
        versions.bump(affected);
        return true;
    }
}
