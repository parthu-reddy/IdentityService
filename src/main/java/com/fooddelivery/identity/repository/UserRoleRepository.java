package com.fooddelivery.identity.repository;

import com.fooddelivery.identity.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
    List<UserRole> findByUserIdAndServiceName(UUID userId, String serviceName);
    List<UserRole> findByRoleNameAndServiceName(String roleName, String serviceName);
    void deleteByUserIdAndServiceNameAndRoleName(UUID userId, String serviceName, String roleName);
}
