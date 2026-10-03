package com.fooddelivery.identity.organisation.repository;

import com.fooddelivery.identity.organisation.entity.OrganisationMember;
import com.fooddelivery.common.enums.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.*;

public interface OrganisationMemberRepository extends JpaRepository<OrganisationMember,UUID> {
    boolean existsByOrganisationIdAndUserIdAndStatus(UUID org, UUID user, MembershipStatus status);
    Optional<OrganisationMember> findByOrganisationIdAndUserId(UUID org, UUID user);
    List<OrganisationMember> findAllByUserIdAndStatus(UUID user, MembershipStatus status);
    List<OrganisationMember> findAllByOrganisationIdInAndUserIdAndStatus(Collection<UUID> orgs, UUID user, MembershipStatus status);
    List<OrganisationMember> findAllByOrganisationIdAndStatus(UUID org, MembershipStatus status);
    Slice<OrganisationMember> findAllByOrganisationIdAndStatus(UUID org, MembershipStatus status, Pageable page);
    long countByOrganisationIdAndStatus(UUID org, MembershipStatus status);
}
