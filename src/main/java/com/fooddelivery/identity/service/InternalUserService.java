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
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class InternalUserService {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InternalUserService.class);
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    @Transactional(readOnly = true)
    public UserDTO getUser(UUID userId, String serviceName) {
        AppUser user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        List<UserRole> roles = userRoleRepository.findByUserIdAndServiceName(userId, serviceName);
        List<RoleName> roleNames = roles.stream().map(UserRole::getRoleName).collect(Collectors.toList());
        return UserDTO.builder().id(user.getId()).phoneNumber(user.getPhoneNumber()).isActive(user.isActive()).roles(roleNames).build();
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getUsersByRole(RoleName roleName, String serviceName) {
        List<UserRole> roles = userRoleRepository.findByRoleNameAndServiceName(roleName, serviceName);
        return roles.stream().map(role -> {
            AppUser user = role.getUser();
            return UserDTO.builder().id(user.getId()).phoneNumber(user.getPhoneNumber()).isActive(user.isActive()).roles(List.of(role.getRoleName())).build();
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<UserDTO> getUsersByRole(RoleName roleName, String serviceName, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<UserRole> roles = userRoleRepository.findByRoleNameAndServiceName(roleName, serviceName, pageable);
        return roles.map(role -> {
            AppUser user = role.getUser();
            return UserDTO.builder().id(user.getId()).phoneNumber(user.getPhoneNumber()).isActive(user.isActive()).roles(List.of(role.getRoleName())).build();
        });
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
        user.setActive(isActive);
        userRepository.save(user);
    }

    @Transactional
    public void addRoleToUser(UUID userId, RoleName roleName, String serviceName) {
        AppUser user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        List<UserRole> existingRoles = userRoleRepository.findByUserIdAndServiceName(userId, serviceName);
        if (existingRoles.stream().noneMatch(r -> r.getRoleName().equals(roleName))) {
            userRoleRepository.save(UserRole.builder().user(user).serviceName(serviceName).roleName(roleName).build());
        }
    }

    @Transactional
    public void removeRoleFromUser(UUID userId, RoleName roleName, String serviceName) {
        userRoleRepository.deleteByUserIdAndServiceNameAndRoleName(userId, serviceName, roleName);
    }

    @java.lang.SuppressWarnings("all")
    public InternalUserService(final UserRepository userRepository, final UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }
}
