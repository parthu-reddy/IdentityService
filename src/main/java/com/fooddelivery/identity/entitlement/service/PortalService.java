package com.fooddelivery.identity.entitlement.service;

import com.fooddelivery.common.enums.*;
import com.fooddelivery.identity.entitlement.dto.PortalViews.*;
import com.fooddelivery.identity.entitlement.entity.BusinessEntitlement;
import com.fooddelivery.identity.entitlement.entity.KitchenEntitlement;
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
        var user = entitlements.requireActiveUser(userId);
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
        // Kitchen staff are not members: outlet details come from GET /api/v1/kitchen/me/workplaces.
        var kitchen = entitlements.liveKitchen(List.of(user));
        var kitchenOrgs = entitlements.kitchenOrganisations(kitchen);
        var kitchenOpen = !kitchenOrgs.isEmpty();
        return List.of(
                new PortalView(Portal.CUSTOMER, State.AVAILABLE, List.of(), null, null),
                businessPortal(Portal.RESTAURANT, restaurants),
                new PortalView(
                        Portal.DELIVERY,
                        rider.map(e -> applicationState(e.getApplicationStatus()))
                                .orElse(State.NOT_STARTED),
                        List.of(),
                        rider.map(e -> e.getReason()).orElse(null),
                        null),
                businessPortal(Portal.BUSINESS, orgViews),
                businessPortal(Portal.ADS, ads),
                new PortalView(
                        Portal.KITCHEN,
                        kitchen.isEmpty()
                                ? State.UNAVAILABLE
                                : kitchenOpen ? State.AVAILABLE : State.SUSPENDED,
                        List.of(),
                        kitchen.isEmpty() || kitchenOpen
                                ? null
                                : "Restaurant access is suspended or closed",
                        kitchenOpen ? kitchenRoles(kitchen, kitchenOrgs) : null),
                new PortalView(
                        Portal.ADMIN,
                        entitlements.isAdministrator(userId)
                                ? State.STEP_UP_REQUIRED
                                : State.UNAVAILABLE,
                        List.of(),
                        null,
                        null));
    }

    /** Every role held in an open kitchen, in {@link KitchenRole} order; rows in closed organisations name nothing. */
    private static List<KitchenRole> kitchenRoles(List<KitchenEntitlement> live, Set<UUID> openOrgs) {
        var held = EnumSet.noneOf(KitchenRole.class);
        live.stream()
                .filter(k -> openOrgs.contains(k.getOrganisationId()))
                .forEach(
                        k -> {
                            if (k.isManager()) held.add(KitchenRole.MANAGER);
                            if (k.isChef()) held.add(KitchenRole.CHEF);
                            if (k.isWaiter()) held.add(KitchenRole.WAITER);
                        });
        return List.copyOf(held);
    }

    private static PortalView businessPortal(Portal portal, List<OrganisationPortal> orgs) {
        if (orgs.isEmpty()) return new PortalView(portal, State.NOT_STARTED, List.of(), null, null);
        var active = orgs.stream().filter(o -> o.status() == OrganisationStatus.ACTIVE).toList();
        if (active.isEmpty())
            return new PortalView(
                    portal, State.SUSPENDED, orgs, "Organisation access is suspended or closed", null);
        var items = active.stream().flatMap(o -> o.businesses().stream()).toList();
        if (items.stream().anyMatch(b -> b.applicationStatus() == ApplicationStatus.APPROVED))
            return new PortalView(portal, State.AVAILABLE, orgs, null, null);
        if (items.isEmpty()) return new PortalView(portal, State.ACTION_REQUIRED, orgs, null, null);
        var selected =
                items.stream()
                        .min(Comparator.comparingInt(b -> priority(b.applicationStatus())))
                        .orElseThrow();
        return new PortalView(
                portal, applicationState(selected.applicationStatus()), orgs, selected.reason(), null);
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
