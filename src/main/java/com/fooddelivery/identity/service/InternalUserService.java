package com.fooddelivery.identity.service;

import com.fooddelivery.identity.dto.UserDTO;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.entity.UserRole;
import com.fooddelivery.identity.repository.UserRepository;
import com.fooddelivery.identity.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fooddelivery.common.enums.RoleName;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InternalUserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    @Transactional(readOnly = true)
    public UserDTO getUser(UUID userId, String serviceName) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        
        List<UserRole> roles = userRoleRepository.findByUserIdAndServiceName(userId, serviceName);
        List<RoleName> roleNames = roles.stream().map(UserRole::getRoleName).collect(Collectors.toList());

        return UserDTO.builder()
                .id(user.getId())
                .phoneNumber(user.getPhoneNumber())
                .roles(roleNames)
                .build();
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getUsersByRole(RoleName roleName, String serviceName) {
        List<UserRole> roles = userRoleRepository.findByRoleNameAndServiceName(roleName, serviceName);
        
        return roles.stream().map(role -> {
            AppUser user = role.getUser();
            return UserDTO.builder()
                    .id(user.getId())
                    .phoneNumber(user.getPhoneNumber())
                    // Only returning the specific role queried, to avoid an N+1 if not necessary,
                    // or we could query all their roles for this service. Let's just return the single role context.
                    .roles(List.of(role.getRoleName()))
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public void addRoleToUser(UUID userId, RoleName roleName, String serviceName) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
                
        List<UserRole> existingRoles = userRoleRepository.findByUserIdAndServiceName(userId, serviceName);
        if (existingRoles.stream().noneMatch(r -> r.getRoleName().equals(roleName))) {
            userRoleRepository.save(UserRole.builder()
                    .user(user)
                    .serviceName(serviceName)
                    .roleName(roleName)
                    .build());
        }
    }

    @Transactional
    public void removeRoleFromUser(UUID userId, RoleName roleName, String serviceName) {
        userRoleRepository.deleteByUserIdAndServiceNameAndRoleName(userId, serviceName, roleName);
    }
}
