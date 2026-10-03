package com.fooddelivery.identity.organisation.repository;

import com.fooddelivery.identity.organisation.entity.Organisation;
import com.fooddelivery.common.enums.MembershipStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface OrganisationRepository extends JpaRepository<Organisation,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from Organisation o where o.id=:id")
    Optional<Organisation> lockById(@Param("id") UUID id);
    @Query("select o from Organisation o where o.id in (select m.organisationId from OrganisationMember m where m.userId=:userId and m.status=:status)")
    Slice<Organisation> findForUser(@Param("userId") UUID userId, @Param("status") MembershipStatus status, Pageable page);
    Slice<Organisation> findAllBy(Pageable page);
}
