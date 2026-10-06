package com.fooddelivery.identity.service;

import com.fooddelivery.common.enums.RoleName;
import com.fooddelivery.identity.dto.UserDTO;
import com.fooddelivery.identity.entitlement.service.*;
import com.fooddelivery.identity.entity.*;
import com.fooddelivery.identity.repository.*;

import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Person-wide computed access, with only administrative staff assignments writable. */
@Service
@lombok.RequiredArgsConstructor
public class InternalUserService {
    private final UserRepository users;
    private final UserRoleRepository staff;
    private final AuthService auth;
    private final EntitlementService entitlements;
    private final EntitlementVersionPublisher versions;

    @Transactional(readOnly = true)
    public UserDTO getUserForAdmin(UUID id) {
        return toDtos(List.of(requireUser(id))).get(0);
    }

    @Transactional(readOnly = true)
    public UserDTO getUserByPhone(String phone) {
        if (phone == null || !phone.matches("\\d{10}"))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A phone number is the 10 digits used to sign in");
        var user =
                users.findByPhoneNumber(phone)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "No user signs in with that number"));
        return toDtos(List.of(user)).get(0);
    }

    @Transactional(readOnly = true)
    public Page<UserDTO> getUsersByRole(RoleName role, Pageable pageable) {
        return toPage(users.findWithEntitlement(role.name(), pageable));
    }

    @Transactional(readOnly = true)
    public Page<UserDTO> getAllUsers(Pageable pageable) {
        return toPage(users.findAll(pageable));
    }

    @Transactional
    public void updateUserStatus(UUID id, boolean active) {
        updateUserStatus(id, active, null);
    }

    @Transactional
    public void updateUserStatus(UUID id, boolean active, UUID actor) {
        // Acquire the shared staff lock first for both suspension and privilege removal.
        var assignments = staff.findByRoleNameForUpdate(RoleName.ADMIN);
        var user = lockUser(id);
        if (user.isActive() == active) return;
        if (!active && assignments.stream().anyMatch(r -> r.getUser().getId().equals(id)))
            preventAdminLoss(id, actor, assignments);
        user.setActive(active);
        users.saveAndFlush(user);
        versions.bump(List.of(id));
        revokeAfterCommit(id);
    }

    @Transactional
    public void addRoleToUser(UUID id, RoleName role) {
        requireStaffRole(role);
        staff.findByRoleNameForUpdate(RoleName.ADMIN);
        var user = lockUser(id);
        if (staff.findByUserId(id).stream().anyMatch(r -> r.getRoleName() == role)) return;
        staff.saveAndFlush(UserRole.builder().user(user).roleName(role).build());
        versions.bump(List.of(id));
        revokeAfterCommit(id);
    }

    @Transactional
    public void removeRoleFromUser(UUID id, RoleName role) {
        removeRoleFromUser(id, role, null);
    }

    @Transactional
    public void removeRoleFromUser(UUID id, RoleName role, UUID actor) {
        requireStaffRole(role);
        var assignments = staff.findByRoleNameForUpdate(RoleName.ADMIN);
        lockUser(id);
        if (assignments.stream().noneMatch(r -> r.getUser().getId().equals(id))) return;
        preventAdminLoss(id, actor, assignments);
        staff.deleteByUserIdAndRoleName(id, role);
        staff.flush();
        versions.bump(List.of(id));
        revokeAfterCommit(id);
    }

    private static void requireStaffRole(RoleName role) {
        if (role != RoleName.ADMIN)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Portal access is computed from approved applications and organisation"
                        + " membership");
    }

    private static void preventAdminLoss(UUID id, UUID actor, List<UserRole> assignments) {
        if (id.equals(actor))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Administrators cannot remove their own access");
        var active =
                assignments.stream()
                        .map(UserRole::getUser)
                        .filter(AppUser::isActive)
                        .map(AppUser::getId)
                        .distinct()
                        .toList();
        if (active.contains(id) && active.size() <= 1)
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "At least one active administrator must remain assigned");
    }

    private AppUser requireUser(UUID id) {
        return users.findById(id)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private AppUser lockUser(UUID id) {
        return users.lockById(id)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private List<UserDTO> toDtos(List<AppUser> people) {
        var computed = entitlements.allRolesFor(people);
        return people.stream()
                .map(
                        u ->
                                UserDTO.builder()
                                        .id(u.getId())
                                        .phoneNumber(u.getPhoneNumber())
                                        .isActive(u.isActive())
                                        .roles(computed.getOrDefault(u.getId(), List.of()))
                                        .build())
                .toList();
    }

    private Page<UserDTO> toPage(Page<AppUser> page) {
        return new PageImpl<>(
                toDtos(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    private void revokeAfterCommit(UUID id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            auth.removeAllSessions(id);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        auth.removeAllSessions(id);
                    }
                });
    }
}
