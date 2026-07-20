package com.fooddelivery.identity.repository;

import com.fooddelivery.identity.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import com.fooddelivery.common.enums.RoleName;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
    List<UserRole> findByUserIdAndServiceName(UUID userId, String serviceName);
    List<UserRole> findByRoleNameAndServiceName(RoleName roleName, String serviceName);
    void deleteByUserIdAndServiceNameAndRoleName(UUID userId, String serviceName, RoleName roleName);
}
