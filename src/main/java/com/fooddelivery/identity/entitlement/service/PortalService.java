package com.fooddelivery.identity.entitlement.service;

import com.fooddelivery.common.enums.*;
import com.fooddelivery.identity.entitlement.dto.PortalViews.*;
import com.fooddelivery.identity.entitlement.entity.BusinessEntitlement;
import com.fooddelivery.identity.entitlement.repository.*;
import com.fooddelivery.identity.organisation.entity.*;
import com.fooddelivery.identity.organisation.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@lombok.RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortalService {
    private final EntitlementService entitlements;
    private final OrganisationMemberRepository members;
    private final OrganisationRepository organisations;
    private final BusinessEntitlementRepository businesses;
    private final DeliveryEntitlementRepository delivery;

    public List<PortalView> portals(UUID userId) {
        entitlements.requireActiveUser(userId);
        var memberships = members.findAllByUserIdAndStatus(userId, MembershipStatus.ACTIVE);
        var orgs =
                organisations.findAllById(
                        memberships.stream().map(OrganisationMember::getOrganisationId).toList());
        var byOrg =
                businesses
                        .findAllByOrganisationIdIn(orgs.stream().map(Organisation::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(BusinessEntitlement::getOrganisationId));
        var roleByOrg =
                memberships.stream()
                        .collect(
                                Collectors.toMap(
                                        OrganisationMember::getOrganisationId,
                                        OrganisationMember::getRole));
        var orgViews =
                orgs.stream()
                        .sorted(
                                Comparator.comparing(Organisation::getCreatedAt)
                                        .thenComparing(Organisation::getId))
                        .map(
                                o ->
                                        new OrganisationPortal(
                                                o.getId(),
                                                o.getDisplayName(),
                                                roleByOrg.get(o.getId()),
                                                o.getStatus(),
                                                byOrg.getOrDefault(o.getId(), List.of()).stream()
                                                        .map(PortalService::businessView)
                                                        .toList()))
                        .toList();
        var restaurants =
                orgViews.stream()
                        .filter(
                                o ->
                                        o.businesses().stream()
                                                .anyMatch(
                                                        b ->
                                                                b.businessType()
                                                                        == BusinessType.RESTAURANT))
                        .toList();
        var ads =
                orgViews.stream()
                        .filter(o -> o.role().grants(OrganisationPermission.ADS_VIEW))
                        .toList();
        var rider = delivery.findById(userId);
        return List.of(
                new PortalView(Portal.CUSTOMER, State.AVAILABLE, List.of(), null),
                businessPortal(Portal.RESTAURANT, restaurants),
                new PortalView(
                        Portal.DELIVERY,
                        rider.map(e -> applicationState(e.getApplicationStatus()))
                                .orElse(State.NOT_STARTED),
                        List.of(),
                        rider.map(e -> e.getReason()).orElse(null)),
                businessPortal(Portal.BUSINESS, orgViews),
                businessPortal(Portal.ADS, ads),
                new PortalView(
                        Portal.ADMIN,
                        entitlements.isAdministrator(userId)
                                ? State.STEP_UP_REQUIRED
                                : State.UNAVAILABLE,
                        List.of(),
                        null));
    }

    private static PortalView businessPortal(Portal portal, List<OrganisationPortal> orgs) {
        if (orgs.isEmpty()) return new PortalView(portal, State.NOT_STARTED, List.of(), null);
        var active = orgs.stream().filter(o -> o.status() == OrganisationStatus.ACTIVE).toList();
        if (active.isEmpty())
            return new PortalView(
                    portal, State.SUSPENDED, orgs, "Organisation access is suspended or closed");
        var items = active.stream().flatMap(o -> o.businesses().stream()).toList();
        if (items.stream().anyMatch(b -> b.applicationStatus() == ApplicationStatus.APPROVED))
            return new PortalView(portal, State.AVAILABLE, orgs, null);
        if (items.isEmpty()) return new PortalView(portal, State.ACTION_REQUIRED, orgs, null);
        var selected =
                items.stream()
                        .min(Comparator.comparingInt(b -> priority(b.applicationStatus())))
                        .orElseThrow();
        return new PortalView(
                portal, applicationState(selected.applicationStatus()), orgs, selected.reason());
    }

    private static int priority(ApplicationStatus status) {
        return switch (status) {
            case APPROVED -> 0;
            case IN_REVIEW, SUBMITTED -> 1;
            case DRAFT, REJECTED -> 2;
            case SUSPENDED -> 3;
        };
    }

    static State applicationState(ApplicationStatus status) {
        return switch (status) {
            case APPROVED -> State.AVAILABLE;
            case SUBMITTED, IN_REVIEW -> State.PENDING_REVIEW;
            case DRAFT, REJECTED -> State.ACTION_REQUIRED;
            case SUSPENDED -> State.SUSPENDED;
        };
    }

    public static BusinessView businessView(BusinessEntitlement item) {
        return new BusinessView(
                item.getBusinessType(),
                item.getBusinessId(),
                item.getApplicationStatus(),
                item.getReason());
    }
}
