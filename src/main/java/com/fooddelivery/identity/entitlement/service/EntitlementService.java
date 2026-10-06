package com.fooddelivery.identity.entitlement.service;

import com.fooddelivery.common.enums.*;
import com.fooddelivery.identity.entitlement.repository.*;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.organisation.entity.*;
import com.fooddelivery.identity.organisation.repository.*;
import com.fooddelivery.identity.repository.*;

import io.micrometer.observation.annotation.Observed;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@lombok.RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntitlementService {
    private final UserRepository users;
    private final UserRoleRepository staff;
    private final OrganisationMemberRepository members;
    private final OrganisationRepository organisations;
    private final BusinessEntitlementRepository businesses;
    private final DeliveryEntitlementRepository delivery;

    public AppUser requireActiveUser(UUID userId) {
        return users.findById(userId)
                .filter(AppUser::isActive)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED, "Account is unavailable"));
    }

    @Observed(name = "auth.entitlements.roles")
    public List<RoleName> rolesFor(UUID userId) {
        requireActiveUser(userId);
        var roles = EnumSet.of(RoleName.CUSTOMER);
        delivery.findById(userId)
                .filter(e -> e.getApplicationStatus() == ApplicationStatus.APPROVED)
                .ifPresent(e -> roles.add(RoleName.DELIVERY));
        var memberships = members.findAllByUserIdAndStatus(userId, MembershipStatus.ACTIVE);
        var active =
                organisations
                        .findAllById(
                                memberships.stream()
                                        .map(OrganisationMember::getOrganisationId)
                                        .toList())
                        .stream()
                        .filter(o -> o.getStatus() == OrganisationStatus.ACTIVE)
                        .map(Organisation::getId)
                        .toList();
        if (!active.isEmpty()) {
            for (var business : businesses.findAllByOrganisationIdIn(active)) {
                if (business.getApplicationStatus() != ApplicationStatus.APPROVED) continue;
                roles.add(RoleName.BUSINESS);
                if (business.getBusinessType() == BusinessType.RESTAURANT)
                    roles.add(RoleName.RESTAURANT);
            }
        }
        // Staff assignments can never elevate the everyday token.
        return List.copyOf(roles);
    }

    public boolean isAdministrator(UUID userId) {
        return requireActiveUser(userId) != null
                && staff.findByUserId(userId).stream()
                        .anyMatch(r -> r.getRoleName() == RoleName.ADMIN);
    }

    public boolean administratorWithPhone(String phone) {
        return users.findByPhoneNumber(phone)
                .filter(AppUser::isActive)
                .map(
                        u ->
                                staff.findByUserId(u.getId()).stream()
                                        .anyMatch(r -> r.getRoleName() == RoleName.ADMIN))
                .orElse(false);
    }

    public List<RoleName> allRolesFor(UUID userId) {
        var roles = new ArrayList<>(rolesFor(userId));
        if (isAdministrator(userId)) roles.add(RoleName.ADMIN);
        return List.copyOf(roles);
    }

    /** Administrative views stay available for suspended people, without granting token roles. */
    public Map<UUID, List<RoleName>> allRolesFor(Collection<AppUser> people) {
        if (people.isEmpty()) return Map.of();
        var ids = people.stream().map(AppUser::getId).toList();
        var result = new LinkedHashMap<UUID, EnumSet<RoleName>>();
        people.forEach(
                u ->
                        result.put(
                                u.getId(),
                                u.isActive()
                                        ? EnumSet.of(RoleName.CUSTOMER)
                                        : EnumSet.noneOf(RoleName.class)));
        var activePeople =
                people.stream()
                        .filter(AppUser::isActive)
                        .map(AppUser::getId)
                        .collect(java.util.stream.Collectors.toSet());
        delivery.findAllById(ids).stream()
                .filter(
                        e ->
                                activePeople.contains(e.getUserId())
                                        && e.getApplicationStatus() == ApplicationStatus.APPROVED)
                .forEach(e -> result.get(e.getUserId()).add(RoleName.DELIVERY));
        var memberships = members.findAllByUserIdInAndStatus(ids, MembershipStatus.ACTIVE);
        var activeOrgs =
                organisations
                        .findAllById(
                                memberships.stream()
                                        .map(OrganisationMember::getOrganisationId)
                                        .distinct()
                                        .toList())
                        .stream()
                        .filter(o -> o.getStatus() == OrganisationStatus.ACTIVE)
                        .map(Organisation::getId)
                        .collect(java.util.stream.Collectors.toSet());
        var approved = new HashMap<UUID, EnumSet<RoleName>>();
        if (!activeOrgs.isEmpty())
            businesses.findAllByOrganisationIdIn(activeOrgs).stream()
                    .filter(b -> b.getApplicationStatus() == ApplicationStatus.APPROVED)
                    .forEach(
                            b -> {
                                var roles =
                                        approved.computeIfAbsent(
                                                b.getOrganisationId(),
                                                id -> EnumSet.of(RoleName.BUSINESS));
                                if (b.getBusinessType() == BusinessType.RESTAURANT)
                                    roles.add(RoleName.RESTAURANT);
                            });
        memberships.stream()
                .filter(m -> activePeople.contains(m.getUserId()))
                .forEach(
                        m -> {
                            var roles = approved.get(m.getOrganisationId());
                            if (roles != null) result.get(m.getUserId()).addAll(roles);
                        });
        staff.findByUserIdIn(ids).stream()
                .filter(r -> r.getRoleName() == RoleName.ADMIN)
                .forEach(r -> result.get(r.getUser().getId()).add(RoleName.ADMIN));
        var view = new LinkedHashMap<UUID, List<RoleName>>();
        result.forEach((id, roles) -> view.put(id, List.copyOf(roles)));
        return Map.copyOf(view);
    }
}
