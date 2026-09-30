package com.fooddelivery.identity.service;

import com.fooddelivery.identity.dto.UserDTO;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.entity.UserRole;
import com.fooddelivery.identity.repository.UserRepository;
import com.fooddelivery.identity.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fooddelivery.common.enums.RoleName;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@lombok.extern.slf4j.Slf4j
public class InternalUserService {
    @java.lang.SuppressWarnings("all")

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public UserDTO getUser(UUID userId, String serviceName) {
        AppUser user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        List<UserRole> roles = userRoleRepository.findByUserIdAndServiceName(userId, serviceName);
        return toUserDto(user, roles);
    }

    /** Full role view for the administrator-facing user-management surface. */
    @Transactional(readOnly = true)
    public UserDTO getUserForAdmin(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toUserDto(user, userRoleRepository.findByUserId(userId));
    }

    /**
     * The user who signs in with this number, with every role they hold. Support looks customers
     * up this way: the admin search box says "User ID / Phone", and a phone went to GET /{id}.
     * The login stores the 10 digits the sign-in form sends (AuthForm strips everything else), so
     * that is the only form looked up; the admin screen normalises what was typed.
     */
    @Transactional(readOnly = true)
    public UserDTO getUserByPhone(String phoneNumber) {
        if (phoneNumber == null || !phoneNumber.matches("\\d{10}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A phone number is the 10 digits used to sign in");
        }
        AppUser user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No user signs in with that number"));
        List<RoleName> roleNames = userRoleRepository.findByUserId(user.getId()).stream()
                .map(UserRole::getRoleName).collect(Collectors.toList());
        return UserDTO.builder().id(user.getId()).phoneNumber(user.getPhoneNumber()).isActive(user.isActive()).roles(roleNames).build();
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getUsersByRole(RoleName roleName, String serviceName) {
        List<UserRole> roles = userRoleRepository.findByRoleNameAndServiceName(roleName, serviceName);
        return roles.stream().map(role -> toUserDto(role.getUser(), List.of(role))).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<UserDTO> getUsersByRole(RoleName roleName, String serviceName, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<UserRole> roles = userRoleRepository.findByRoleNameAndServiceName(roleName, serviceName, pageable);
        return roles.map(role -> toUserDto(role.getUser(), List.of(role)));
    }

    /**
     * Role lookup for admins. The portal service name comes from the assignment, never from an
     * administrator-supplied request header.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<UserDTO> getUsersByRole(RoleName roleName,
                                                                         org.springframework.data.domain.Pageable pageable) {
        return userRoleRepository.findByRoleName(roleName, pageable)
                .map(role -> toUserDto(role.getUser(), List.of(role)));
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<UserDTO> getAllUsers(org.springframework.data.domain.Pageable pageable) {
        return userRepository.findAll(pageable).map(user -> {
            List<UserRole> userRoles = userRoleRepository.findByUserId(user.getId());
            List<RoleName> roleNames = userRoles.stream().map(UserRole::getRoleName).collect(Collectors.toList());
            return UserDTO.builder()
                    .id(user.getId())
                    .phoneNumber(user.getPhoneNumber())
                    .isActive(user.isActive())
                    .roles(roleNames)
                    .build();
        });
    }

    @Transactional
    public void updateUserStatus(UUID userId, boolean isActive) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.isActive() != isActive) {
            user.setActive(isActive);
            userRepository.save(user);
            revokeSessionsAfterCommit(userId);
        }
    }

    @Transactional
    public void addRoleToUser(UUID userId, RoleName roleName) {
        AppUser user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        List<UserRole> existingRoles = userRoleRepository.findByUserId(userId);
        if (existingRoles.stream().noneMatch(r -> r.getRoleName().equals(roleName))) {
            AuthPortal portal = AuthPortal.forRole(roleName);
            userRoleRepository.save(UserRole.builder()
                    .user(user)
                    .serviceName(portal.canonicalServiceName())
                    .roleName(roleName)
                    .build());
            revokeSessionsAfterCommit(userId);
        }
    }

    @Transactional
    public void removeRoleFromUser(UUID userId, RoleName roleName) {
        removeRoleFromUser(userId, roleName, null);
    }

    @Transactional
    public void removeRoleFromUser(UUID userId, RoleName roleName, UUID actingUserId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        boolean assigned = userRoleRepository.findByUserId(userId).stream()
                .anyMatch(role -> role.getRoleName() == roleName);
        if (assigned) {
            preventUnsafeAdminRoleRemoval(userId, roleName, actingUserId);
            // Earlier releases stored a portal role under caller-controlled service names. Remove
            // every legacy alias so a removed role cannot reappear on a different portal login.
            userRoleRepository.deleteByUserIdAndRoleName(userId, roleName);
            revokeSessionsAfterCommit(userId);
        }
    }

    /**
     * Lock every ADMIN assignment before deciding whether one may be deleted.  The table permits
     * legacy service-name aliases, so administrator availability is counted by distinct user id.
     */
    private void preventUnsafeAdminRoleRemoval(UUID userId, RoleName roleName, UUID actingUserId) {
        if (roleName != RoleName.ADMIN) {
            return;
        }
        if (userId.equals(actingUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Administrators cannot remove their own admin role");
        }
        Set<UUID> adminUserIds = userRoleRepository.findByRoleNameForUpdate(RoleName.ADMIN).stream()
                .map(UserRole::getUser)
                .map(AppUser::getId)
                .collect(Collectors.toSet());
        if (adminUserIds.contains(userId) && adminUserIds.size() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "At least one administrator must remain assigned");
        }
    }

    private UserDTO toUserDto(AppUser user, List<UserRole> roles) {
        List<RoleName> roleNames = roles.stream().map(UserRole::getRoleName).distinct().collect(Collectors.toList());
        return UserDTO.builder().id(user.getId()).phoneNumber(user.getPhoneNumber())
                .isActive(user.isActive()).roles(roleNames).build();
    }

    /** Revoke only after the database change commits, so a rolled-back admin write keeps access. */
    private void revokeSessionsAfterCommit(UUID userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            authService.removeAllSessions(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                authService.removeAllSessions(userId);
            }
        });
    }

    @java.lang.SuppressWarnings("all")
    public InternalUserService(final UserRepository userRepository, final UserRoleRepository userRoleRepository,
                               final AuthService authService) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.authService = authService;
    }
}
