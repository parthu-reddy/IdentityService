package com.fooddelivery.identity.service;

import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The one place an account is created: by phone, on its first sign-in or when a dine-in guest consents to an order
 * placed for that phone. Both callers get the same status check, so a deactivated account is never revived.
 */
@Service
@lombok.RequiredArgsConstructor
public class UserProvisioner {
    private final UserRepository users;

    public record Provisioned(AppUser user, boolean created) {}

    // noRollbackFor: a dine-in consent records its refusal in the caller's transaction after this throws.
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = AccountUnavailableException.class)
    public Provisioned findOrCreate(String phone) {
        var existing = users.findByPhoneNumber(phone);
        var user =
                existing.orElseGet(
                        () ->
                                users.saveAndFlush(
                                        AppUser.builder()
                                                .phoneNumber(phone)
                                                .isActive(true)
                                                .build()));
        return new Provisioned(lockActive(user.getId()), existing.isEmpty());
    }

    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = AccountUnavailableException.class)
    public AppUser lockActive(UUID userId) {
        return users.lockById(userId)
                .filter(AppUser::isActive)
                .orElseThrow(AccountUnavailableException::new);
    }
}
