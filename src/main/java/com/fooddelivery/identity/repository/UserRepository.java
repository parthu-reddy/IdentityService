package com.fooddelivery.identity.repository;

import com.fooddelivery.identity.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByPhoneNumber(String phoneNumber);
}
