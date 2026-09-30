package com.fooddelivery.identity.repository;

import com.fooddelivery.identity.entity.UserRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import com.fooddelivery.common.enums.RoleName;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
    List<UserRole> findByUserIdAndServiceName(UUID userId, String serviceName);
    List<UserRole> findByUserId(UUID userId);

    List<UserRole> findByRoleName(RoleName roleName);

    /** Serializes administrator-role removals so two requests cannot remove the final admins. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ur from UserRole ur where ur.roleName = :roleName")
    List<UserRole> findByRoleNameForUpdate(@Param("roleName") RoleName roleName);

    List<UserRole> findByRoleNameAndServiceName(RoleName roleName, String serviceName);
    org.springframework.data.domain.Page<UserRole> findByRoleName(RoleName roleName, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<UserRole> findByRoleNameAndServiceName(RoleName roleName, String serviceName, org.springframework.data.domain.Pageable pageable);
    void deleteByUserIdAndServiceNameAndRoleName(UUID userId, String serviceName, RoleName roleName);
    void deleteByUserIdAndRoleName(UUID userId, RoleName roleName);
}
