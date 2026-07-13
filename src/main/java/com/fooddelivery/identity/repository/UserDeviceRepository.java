package com.fooddelivery.identity.repository;

import com.fooddelivery.identity.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, String> {
    List<UserDevice> findByUserIdOrderByLoginTimeAsc(UUID userId);
    List<UserDevice> findByUserIdAndPortalOrderByLoginTimeAsc(UUID userId, String portal);
}
