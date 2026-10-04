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
    List<UserRole> findByUserId(UUID userId);
    List<UserRole> findByUserIdIn(java.util.Collection<UUID> userIds);

    List<UserRole> findByRoleName(RoleName roleName);

    /** Serializes administrator-role removals so two requests cannot remove the final admins. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ur from UserRole ur join fetch ur.user where ur.roleName = :roleName order by ur.user.id")
    List<UserRole> findByRoleNameForUpdate(@Param("roleName") RoleName roleName);

    org.springframework.data.domain.Page<UserRole> findByRoleName(RoleName roleName, org.springframework.data.domain.Pageable pageable);
    void deleteByUserIdAndRoleName(UUID userId, RoleName roleName);
}
